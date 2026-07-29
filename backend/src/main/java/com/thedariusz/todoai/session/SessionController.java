package com.thedariusz.todoai.session;

import java.net.URI;

import com.thedariusz.todoai.auth.LoginRequest;
import com.thedariusz.todoai.auth.UserResponse;
import com.thedariusz.todoai.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code sessions} resource — login is creating a session, logout is deleting the current one
 * (verb-free per {@code openapi.yaml}; there is no {@code /login} or {@code /logout} action path).
 *
 * <p>The SPA speaks JSON, and Spring Security ships no JSON login filter, so authentication is
 * driven explicitly here. That means the three things {@code AbstractAuthenticationProcessingFilter}
 * would normally do have to be done by hand — and skipping any of them is a real vulnerability, not
 * a shortcut:
 * <ol>
 *   <li>{@link SessionAuthenticationStrategy#onAuthentication} — rotates the session id (fixation
 *       protection) and the CSRF token, so a token an attacker planted pre-login is dead after it;</li>
 *   <li>the {@link SecurityContext} is put on the holder for the rest of this request;</li>
 *   <li>and persisted through the {@link SecurityContextRepository}, which is what actually writes
 *       the session — without it the login "succeeds" and the very next request is anonymous.</li>
 * </ol>
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

	private final AuthenticationManager authenticationManager;

	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

	private final LogoutHandler logoutHandler;

	/** The chain's own repository (a {@code SecurityConfig} bean), never a private instance — see there. */
	private final SecurityContextRepository securityContextRepository;

	public SessionController(AuthenticationManager authenticationManager,
			SessionAuthenticationStrategy sessionAuthenticationStrategy, LogoutHandler logoutHandler,
			SecurityContextRepository securityContextRepository) {
		this.authenticationManager = authenticationManager;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.logoutHandler = logoutHandler;
		this.securityContextRepository = securityContextRepository;
	}

	/**
	 * Authenticates and establishes the session. A failure throws {@code BadCredentialsException},
	 * which propagates to Spring Security's {@code ExceptionTranslationFilter} and comes back as the
	 * generic 401 Problem JSON from {@code ProblemDetailsSecurityHandler} — identical whether the
	 * email is unknown or the password is wrong, so the response reveals nothing about which emails
	 * exist.
	 */
	@PostMapping
	ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		Authentication authentication = authenticationManager.authenticate(
				UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

		sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
		materializeRotatedCsrfToken(httpRequest);

		SecurityContext context = SecurityContextHolder.createEmptyContext();
		context.setAuthentication(authentication);
		SecurityContextHolder.setContext(context);
		securityContextRepository.saveContext(context, httpRequest, httpResponse);

		UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
		return ResponseEntity.created(URI.create("/api/sessions/current")).body(UserResponse.from(principal));
	}

	/**
	 * Forces the rotated CSRF token onto <em>this</em> response.
	 *
	 * <p>{@code CsrfAuthenticationStrategy} retires the anonymous token by saving {@code null} — which
	 * writes a cookie <em>deletion</em> — and then only defers the replacement behind a supplier. In
	 * the stock form-login flow something downstream reads that supplier; here nothing does, because
	 * {@code CsrfCookieFilter} already ran on the way in and a controller runs after every filter. So
	 * the login response would carry a deletion and no new token, and the SPA's very next mutation
	 * would 403. Resolving the token is what triggers the repository to write the new cookie.
	 */
	private static void materializeRotatedCsrfToken(HttpServletRequest httpRequest) {
		CsrfToken token = (CsrfToken) httpRequest.getAttribute(CsrfToken.class.getName());
		if (token != null) {
			token.getToken();
		}
	}

	@DeleteMapping("/current")
	ResponseEntity<Void> logout(HttpServletRequest httpRequest, HttpServletResponse httpResponse) {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		logoutHandler.logout(httpRequest, httpResponse, authentication);
		return ResponseEntity.noContent().build();
	}
}
