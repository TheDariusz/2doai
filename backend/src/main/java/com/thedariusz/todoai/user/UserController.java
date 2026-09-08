package com.thedariusz.todoai.user;

import java.net.URI;
import java.util.Locale;

import com.thedariusz.todoai.account.AccountDeletionService;
import com.thedariusz.todoai.auth.DeleteAccountRequest;
import com.thedariusz.todoai.auth.EmailVerificationService;
import com.thedariusz.todoai.auth.ReAuthenticationFailedException;
import com.thedariusz.todoai.auth.RegisterRequest;
import com.thedariusz.todoai.auth.RegistrationService;
import com.thedariusz.todoai.auth.UserResponse;
import com.thedariusz.todoai.auth.UserUpdate;
import com.thedariusz.todoai.security.AuthenticatedSession;
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
import org.springframework.web.bind.annotation.PatchMapping;
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

	private final EmailVerificationService verification;

	private final AccountDeletionService accountDeletionService;

	private final PasswordEncoder passwordEncoder;

	private final LogoutHandler logoutHandler;

	private final SessionRegistry sessionRegistry;

	private final UserSettingsService settings;

	private final AuthenticatedSession session;

	public UserController(RegistrationService registrationService, EmailVerificationService verification,
			AccountDeletionService accountDeletionService, PasswordEncoder passwordEncoder,
			LogoutHandler logoutHandler, SessionRegistry sessionRegistry, UserSettingsService settings,
			AuthenticatedSession session) {
		this.registrationService = registrationService;
		this.verification = verification;
		this.accountDeletionService = accountDeletionService;
		this.passwordEncoder = passwordEncoder;
		this.logoutHandler = logoutHandler;
		this.sessionRegistry = sessionRegistry;
		this.settings = settings;
		this.session = session;
	}

	/**
	 * Registers a user. {@code Location} points at {@code /api/users/me} rather than a per-id URL:
	 * the created user is only ever readable as "the current user", and there is no
	 * {@code /users/{id}} endpoint to point at (nor should there be, in a flat single-tenant model).
	 *
	 * <p><b>The two steps are two transactions on purpose</b> (DEV-51). The account is committed first
	 * and the code is issued and mailed afterwards, because SMTP has a ten-second timeout on each leg
	 * and a database connection may not be held across it. What that costs is a window where the row
	 * exists and no code was sent — answered as 503, and self-healing: the next sign-up with this
	 * address takes the unverified row over, and "send again" issues a code for it.
	 */
	@PostMapping
	ResponseEntity<UserResponse> register(@Valid @RequestBody RegisterRequest request, Locale locale) {
		// FR-003 — the account starts in the language of the screen it was created from, which is the
		// language this very request advertised. `locale` is resolved by the LocaleResolver in
		// `i18n/LocaleConfig`, so quality ordering and unsupported tags are already handled.
		AppLanguage language = AppLanguage.of(locale);
		User user = registrationService.register(request.email(), request.password(), language);
		verification.issue(user.getId(), user.getEmail(), language);
		// Not `UserResponse.from(user)`: taking an unverified account over writes the new language with a
		// targeted update, which the row loaded before it cannot see. The language this request asked for
		// is the one that was stored, on both paths.
		return ResponseEntity.created(URI.create("/api/users/me"))
				.body(new UserResponse(user.getId(), user.getEmail(), language));
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
	 * FR-002 — changes the language this account reads in, effective on the current session.
	 *
	 * <p>Two collaborators, because each half is a rule rather than a step:
	 * {@link UserSettingsService#changeLanguage} owns the write and what an unmatched row means, and
	 * {@link AuthenticatedSession#replacePrincipal} owns putting the changed principal back into the
	 * session — without which the switch is real in Postgres and invisible on {@code /me}, which is
	 * answered from the principal with no query. Both javadocs are worth reading before touching this.
	 */
	@PatchMapping("/me")
	UserResponse updateCurrentUser(@Valid @RequestBody UserUpdate request,
			@AuthenticationPrincipal UserPrincipal principal,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		settings.changeLanguage(principal.userId(), request.language());
		UserPrincipal switched = principal.withLanguage(request.language());
		session.replacePrincipal(switched, httpRequest, httpResponse);

		log.info("Account {} switched language to {}", switched.userId(), switched.language());
		return UserResponse.from(switched);
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
		// The address belongs to nobody now, cooldown included — otherwise the next sign-up with it, by
		// anyone, would wait for a code that was sent to an account that no longer exists (DEV-51).
		verification.forget(principal.email());
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
