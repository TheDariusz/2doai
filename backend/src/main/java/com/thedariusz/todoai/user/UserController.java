package com.thedariusz.todoai.user;

import java.net.URI;

import com.thedariusz.todoai.account.AccountDeletionService;
import com.thedariusz.todoai.auth.DeleteAccountRequest;
import com.thedariusz.todoai.auth.ReAuthenticationFailedException;
import com.thedariusz.todoai.auth.RegisterRequest;
import com.thedariusz.todoai.auth.RegistrationService;
import com.thedariusz.todoai.auth.UserResponse;
import com.thedariusz.todoai.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * The {@code users} resource (see {@code openapi.yaml}). Verb-free and resource-oriented per the
 * Zalando review: registration is a {@code POST} of a new user, not a {@code /register} action.
 */
@RestController
@RequestMapping("/api/users")
public class UserController {

	private static final Logger log = LoggerFactory.getLogger(UserController.class);

	private final RegistrationService registrationService;

	private final AccountDeletionService accountDeletionService;

	private final PasswordEncoder passwordEncoder;

	private final LogoutHandler logoutHandler;

	private final SessionRegistry sessionRegistry;

	public UserController(RegistrationService registrationService,
			AccountDeletionService accountDeletionService, PasswordEncoder passwordEncoder,
			LogoutHandler logoutHandler, SessionRegistry sessionRegistry) {
		this.registrationService = registrationService;
		this.accountDeletionService = accountDeletionService;
		this.passwordEncoder = passwordEncoder;
		this.logoutHandler = logoutHandler;
		this.sessionRegistry = sessionRegistry;
	}

	/**
	 * Registers a user. {@code Location} points at {@code /api/users/me} rather than a per-id URL:
	 * the created user is only ever readable as "the current user", and there is no
	 * {@code /users/{id}} endpoint to point at (nor should there be, in a flat single-tenant model).
	 */
	@PostMapping
	ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request) {
		User user = registrationService.register(request.email(), request.password());
		return ResponseEntity.created(URI.create("/api/users/me")).body(UserResponse.from(user));
	}

	/**
	 * The SPA's bootstrap call: who am I? Answered from the authenticated principal with no query —
	 * the session is validated in memory, so an idle app never wakes the database (the Neon
	 * idleness rule in {@code context/foundation/lessons.md}).
	 */
	@GetMapping("/me")
	UserResponse currentUser(@AuthenticationPrincipal UserPrincipal principal) {
		return UserResponse.from(principal);
	}

	/**
	 * FR-019 — permanently erases the user and everything they own, then ends every session they hold.
	 *
	 * <p>The password is re-verified against the hash already carried on the principal (no query:
	 * the session was established from that same hash). A mismatch is reported as <b>403</b>, not the
	 * generic 401: the session is perfectly valid and the user simply mistyped, and a 401 on an
	 * authenticated call is what every SPA reads as "session expired, go to login".
	 *
	 * <p>Sibling sessions are expired explicitly. {@code logout} only ends the calling request's
	 * session, so a phone left logged in would otherwise keep authenticating as a user who no longer
	 * exists — and, once per-user writes land, would fail at commit against a dangling foreign key.
	 */
	@DeleteMapping("/me")
	ResponseEntity<Void> deleteCurrentUser(@Valid @RequestBody DeleteAccountRequest request,
			@AuthenticationPrincipal UserPrincipal principal,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		if (!passwordEncoder.matches(request.password(), principal.passwordHash())) {
			log.warn("Re-authentication failed for account deletion of user {}", principal.userId());
			throw new ReAuthenticationFailedException();
		}
		accountDeletionService.deleteAccount(principal.userId());
		expireOtherSessionsOf(principal);

		try {
			Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
			logoutHandler.logout(httpRequest, httpResponse, authentication);
		}
		catch (RuntimeException ex) {
			// The account is already committed as deleted. Failing the response here would tell the
			// user their deletion failed and invite a retry that can only 404.
			log.error("Account {} was deleted but its session could not be ended", principal.userId(), ex);
		}
		return ResponseEntity.noContent().build();
	}

	private void expireOtherSessionsOf(UserPrincipal principal) {
		sessionRegistry.getAllSessions(principal, false).forEach(SessionInformation::expireNow);
	}
}
