package com.thedariusz.todoai.security;

import java.util.List;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AuthenticationManager;
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
import org.springframework.security.web.authentication.session.ChangeSessionIdAuthenticationStrategy;
import org.springframework.security.web.authentication.session.CompositeSessionAuthenticationStrategy;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
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
 *   <li><b>Session management</b> — left at the SS6 default (session created on authentication,
 *       fixation protection {@code changeSessionId}). Cookie attributes ({@code HttpOnly},
 *       {@code SameSite=Strict}, env-driven {@code Secure}) live in {@code application.properties}.</li>
 * </ul>
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, CsrfTokenRepository csrfTokenRepository,
			ProblemDetailsSecurityHandler problemDetailsSecurityHandler) throws Exception {
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.POST, "/api/users").permitAll()      // register
						.requestMatchers(HttpMethod.POST, "/api/sessions").permitAll()   // login
						.requestMatchers("/api/ping").permitAll()
						.requestMatchers("/actuator/health/**").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf
						.csrfTokenRepository(csrfTokenRepository)
						.csrfTokenRequestHandler(new CsrfTokenRequestAttributeHandler()))
				.addFilterAfter(new CsrfCookieFilter(), BasicAuthenticationFilter.class)
				.exceptionHandling(ex -> ex
						.authenticationEntryPoint(problemDetailsSecurityHandler)
						.accessDeniedHandler(problemDetailsSecurityHandler));
		return http.build();
	}

	/**
	 * Shared by the filter chain and {@link #sessionAuthenticationStrategy} — both must read and
	 * write the <em>same</em> cookie, or the token rotated at login would not be the one validated
	 * on the next mutation.
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
	 * attacker before login is not the id that carries the session after it), and
	 * {@link CsrfAuthenticationStrategy} retires the anonymous CSRF token so a pre-login token
	 * cannot be replayed against the now-authenticated session.
	 */
	@Bean
	SessionAuthenticationStrategy sessionAuthenticationStrategy(CsrfTokenRepository csrfTokenRepository) {
		return new CompositeSessionAuthenticationStrategy(List.of(
				new ChangeSessionIdAuthenticationStrategy(),
				new CsrfAuthenticationStrategy(csrfTokenRepository)));
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
	 * {@link #passwordEncoder()} via the auto-configured {@code DaoAuthenticationProvider}) so the
	 * {@code AuthController} login flow (Phase 2) can authenticate credentials explicitly.
	 */
	@Bean
	AuthenticationManager authenticationManager(AuthenticationConfiguration configuration) throws Exception {
		return configuration.getAuthenticationManager();
	}
}
