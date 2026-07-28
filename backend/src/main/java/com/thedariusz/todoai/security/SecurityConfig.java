package com.thedariusz.todoai.security;

import java.util.List;

import jakarta.servlet.DispatcherType;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.config.annotation.web.configurers.RequestCacheConfigurer;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.CredentialsExpiredException;
import org.springframework.security.config.annotation.authentication.configuration.AuthenticationConfiguration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.crypto.factory.PasswordEncoderFactories;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.logout.CompositeLogoutHandler;
import org.springframework.security.web.authentication.logout.CookieClearingLogoutHandler;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.RegisterSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.authentication.www.BasicAuthenticationFilter;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfTokenRepository;
import org.springframework.security.web.csrf.CsrfTokenRequestAttributeHandler;

/**
 * Wires the decided authentication model (see {@code context/foundation/auth-session-model.md}):
 * a server-side {@code HttpSession} cookie, in-memory on the single always-on Fly machine —
 * <b>not JWT</b>, never Neon-backed sessions.
 *
 * <p>The REST surface follows the Zalando resource-oriented model (verb-free URLs, no {@code /v1}
 * path version — see {@code openapi.yaml}): registration is {@code POST /api/users}, login is
 * {@code POST /api/sessions}, logout is {@code DELETE /api/sessions/current}, the current user is
 * {@code GET /api/users/me}, and account deletion is {@code DELETE /api/users/me}.
 *
 * <p>Key choices:
 * <ul>
 *   <li><b>Authorization</b> — public: {@code POST /api/users} (register), {@code POST /api/sessions}
 *       (login), the {@code /api/ping} smoke path, and {@code /actuator/health/**} (Fly's liveness
 *       probe hits {@code /actuator/health/liveness} <em>through</em> this filter chain, so it must
 *       stay public). The matchers are method-specific so that {@code GET/DELETE /api/users/me} and
 *       {@code DELETE /api/sessions/current} still require authentication. Everything else is
 *       authenticated.</li>
 *   <li><b>Unauthenticated API requests → 401 Problem JSON</b>, not the default login redirect:
 *       the SPA owns navigation, so a gated call must fail with a machine-readable status, never
 *       an HTML page. {@link ProblemDetailsSecurityHandler} also writes 403 failures consistently.</li>
 *   <li><b>CSRF</b> — {@link CookieCsrfTokenRepository#withHttpOnlyFalse()} publishes the token in
 *       an {@code XSRF-TOKEN} cookie the SPA reads and echoes as {@code X-XSRF-TOKEN}; the
 *       {@link CsrfTokenRequestAttributeHandler} is the SPA-safe handler that reads the raw token
 *       from the header (not the BREACH-masked form value). SS6 defers token generation until the
 *       token is read, so a {@link CsrfCookieFilter} (ordered before the authorization filter)
 *       renders it on every request — priming the cookie even on the SPA's unauthenticated
 *       {@code GET /api/users/me} bootstrap, which is rejected 401 before any handler could run.</li>
 *   <li><b>Session management</b> — sessions are created on authentication only. Fixation protection
 *       ({@code changeSessionId}) and CSRF-token rotation are applied by the explicit
 *       {@link #sessionAuthenticationStrategy} bean, because the JSON login path does not run through
 *       the filter that would normally wire them. Cookie attributes ({@code HttpOnly},
 *       {@code SameSite=Strict}, env-driven {@code Secure}) live in {@code application.properties}.</li>
 *   <li><b>No request cache</b> — the default {@code HttpSessionRequestCache} calls
 *       {@code request.getSession()} before the entry point runs, so every anonymous 401 would mint a
 *       30-minute in-memory session on the single machine: an unauthenticated memory-exhaustion lever.
 *       Nothing replays saved requests here — post-login routing is the SPA's job.</li>
 *   <li><b>{@code ERROR} dispatch is permitted</b> — the chain runs on that dispatch by default, so
 *       without this an unhandled 500 on a public endpoint is re-authorized as an anonymous request
 *       and rewritten into a misleading 401.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
			ProblemDetailsSecurityHandler problemDetailsSecurityHandler, SessionRegistry sessionRegistry)
			throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.dispatcherTypeMatchers(DispatcherType.ERROR).permitAll()
						.requestMatchers(HttpMethod.POST, "/api/users").permitAll()      // register
						.requestMatchers(HttpMethod.POST, "/api/sessions").permitAll()   // login
						.requestMatchers("/api/ping").permitAll()
						.requestMatchers("/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
				.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
				.requestCache(RequestCacheConfigurer::disable)
				.sessionManagement(session -> session
						.maximumSessions(-1)
						.sessionRegistry(sessionRegistry)
						.expiredSessionStrategy(event -> problemDetailsSecurityHandler.commence(
								event.getRequest(), event.getResponse(),
								new CredentialsExpiredException("Session ended"))))
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(problemDetailsSecurityHandler)
						.accessDeniedHandler(problemDetailsSecurityHandler));
		return http.build();
	}

	/**
	 * Tracks which sessions belong to which principal, so FR-019 deletion can end the ones it is not
	 * being called from. Sessions live in the single machine's memory, so this registry is the only
	 * thing that knows a user's phone is still logged in while they delete the account on a laptop.
	 *
	 * <p>{@code maximumSessions(-1)} imposes no limit — the registry is wanted for its bookkeeping,
	 * not for concurrency control — but it is what puts {@code ConcurrentSessionFilter} in the chain,
	 * which is what turns an expired session into a 401 on its next request.
	 */
	@Bean
	SessionRegistry sessionRegistry() {
		return new SessionRegistryImpl();
	}

	/** Feeds session-destroyed events to {@link #sessionRegistry()} so it does not leak dead entries. */
	@Bean
	HttpSessionEventPublisher httpSessionEventPublisher() {
		return new HttpSessionEventPublisher();
	}

	/**
	 * One bean so the filter chain and {@link #sessionAuthenticationStrategy} can never drift apart on
	 * cookie name or path — a mismatch there would silently break the post-login double-submit.
	 */
	@Bean
	CsrfTokenRepository csrfTokenRepository() {
		return CookieCsrfTokenRepository.withHttpOnlyFalse();
	}

	/**
	 * What {@code AbstractAuthenticationProcessingFilter} would apply on a form login — needed
	 * explicitly because the SPA logs in through a JSON controller instead
	 * ({@code SessionController}). Both members are security-load-bearing:
	 * {@link ChangeSessionIdAuthenticationStrategy} defeats session fixation (an id fixed by an
	 * attacker before login is not the id that carries the session after it),
	 * {@link CsrfAuthenticationStrategy} retires the anonymous CSRF token so a pre-login token
	 * cannot be replayed against the now-authenticated session, and
	 * {@link RegisterSessionAuthenticationStrategy} records the new session against its principal so
	 * account deletion can later find and end it. Ordering matters: the session must be rotated
	 * before it is registered, or the registry would hold the pre-login id.
	 */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository,
			SessionRegistry sessionRegistry) {
		return new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository),
				new RegisterSessionAuthenticationStrategy(sessionRegistry)));
	}

	@Bean
	LogoutHandler apiLogoutHandler() {
		return new CompositeLogoutHandler(
				new SecurityContextLogoutHandler(),
				new CookieClearingLogoutHandler("JSESSIONID"));
	}

	/**
	 * Delegating encoder — encodes new passwords with BCrypt (the {@code {bcrypt}} prefix) while
	 * still able to verify any other prefixed hash, so the encoding can be upgraded later without a
	 * migration.
	 */
	@Bean
	PasswordEncoder passwordEncoder() {
		return PasswordEncoderFactories.createDelegatingPasswordEncoder();
	}

	/**
	 * Exposes the {@link AuthenticationManager} (backed by {@code AppUserDetailsService} +
	 * {@link #passwordEncoder()} via the auto-configured {@code DaoAuthenticationProvider}) so
	 * {@link com.thedariusz.todoai.session.SessionController} can authenticate credentials explicitly.
	 */
	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
