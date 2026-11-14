package com.thedariusz.todoai.session;

import java.net.URI;

import com.thedariusz.todoai.auth.EmailVerificationService;
import com.thedariusz.todoai.auth.LoginRequest;
import com.thedariusz.todoai.auth.UserResponse;
import com.thedariusz.todoai.auth.VerificationFailedException;
import com.thedariusz.todoai.security.AuthenticatedSession;
import com.thedariusz.todoai.security.UserPrincipal;
import com.thedariusz.todoai.user.CodePurpose;
import com.thedariusz.todoai.user.User;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.dao.DataAccessException;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.transaction.TransactionException;
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
 *
 * <p><b>Two credentials, one session-creation path</b> (passwordless sign-in). A password goes through
 * the {@link AuthenticationManager}; a mailed code is redeemed by {@link EmailVerificationService} and
 * turned into an authenticated token here. There is deliberately no second
 * {@code AuthenticationProvider} bean for the code — Spring adopts a single provider bean into the
 * global manager, and a second one would silently make it adopt neither. Both paths then run the same
 * three steps above, so a code sign-in rotates the session id and the CSRF token exactly as a password
 * sign-in does.
 */
@RestController
@RequestMapping("/api/sessions")
public class SessionController {

	private final AuthenticationManager authenticationManager;

	private final SessionAuthenticationStrategy sessionAuthenticationStrategy;

	private final LogoutHandler logoutHandler;

	/** Owns step 3 below — the write-back through the chain's own {@code SecurityContextRepository}. */
	private final AuthenticatedSession session;

	private final EmailVerificationService verification;

	public SessionController(AuthenticationManager authenticationManager,
			SessionAuthenticationStrategy sessionAuthenticationStrategy, LogoutHandler logoutHandler,
			AuthenticatedSession session, EmailVerificationService verification) {
		this.authenticationManager = authenticationManager;
		this.sessionAuthenticationStrategy = sessionAuthenticationStrategy;
		this.logoutHandler = logoutHandler;
		this.session = session;
		this.verification = verification;
	}

	/**
	 * Authenticates and establishes the session. A failure throws {@code BadCredentialsException},
	 * which propagates to Spring Security's {@code ExceptionTranslationFilter} and comes back as the
	 * generic 401 Problem JSON from {@code ProblemDetailsSecurityHandler} — identical whether the
	 * email is unknown, the password is wrong, or the code is wrong, expired, spent or out of guesses,
	 * so the response reveals nothing about which emails exist.
	 */
	@PostMapping
	ResponseEntity<UserResponse> login(@Valid @RequestBody LoginRequest request,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		Authentication authentication = request.signsInByCode()
				? redeemCode(request)
				: authenticationManager.authenticate(
						UsernamePasswordAuthenticationToken.unauthenticated(request.email(), request.password()));

		sessionAuthenticationStrategy.onAuthentication(authentication, httpRequest, httpResponse);
		materializeRotatedCsrfToken(httpRequest);

		session.establish(authentication, httpRequest, httpResponse);

		UserPrincipal principal = (UserPrincipal) authentication.getPrincipal();
		return ResponseEntity.created(URI.create("/api/sessions/current")).body(UserResponse.from(principal));
	}

	/**
	 * Spends the code and answers with the authenticated token the password path would have produced.
	 * The redemption has already committed — including a wrong guess's charge — by the time either
	 * exception is translated here, which is why the translation happens outside it.
	 *
	 * <p>A refused code is bad credentials: the generic 401. A database failure is a service failure,
	 * the 503 the password path gets from {@code DaoAuthenticationProvider} wrapping it in
	 * {@link AuthenticationServiceException} — telling every user their code is wrong during an outage
	 * would be the lie {@code ProblemDetailsSecurityHandler} exists to prevent.
	 */
	private Authentication redeemCode(LoginRequest request) {
		User account;
		try {
			account = verification.redeem(request.email(), request.code(), CodePurpose.SIGN_IN);
		}
		catch (VerificationFailedException ex) {
			// No cause on purpose: Spring MVC matches @ExceptionHandler methods against an exception's
			// causes too, so carrying this one would let ApiExceptionHandler answer the address-proof 403
			// instead of the generic 401. The service has already logged why it refused.
			throw new BadCredentialsException("The code was refused");
		}
		catch (DataAccessException | TransactionException ex) {
			throw new AuthenticationServiceException("A code sign-in could not be completed", ex);
		}
		UserPrincipal principal = UserPrincipal.from(account);
		return UsernamePasswordAuthenticationToken.authenticated(principal, null, principal.getAuthorities());
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
