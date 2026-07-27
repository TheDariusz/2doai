package com.thedariusz.todoai.user;

import java.net.URI;

import com.thedariusz.todoai.account.AccountDeletionService;
import com.thedariusz.todoai.auth.DeleteAccountRequest;
import com.thedariusz.todoai.auth.RegisterRequest;
import com.thedariusz.todoai.auth.RegistrationService;
import com.thedariusz.todoai.auth.UserResponse;
import com.thedariusz.todoai.security.UserPrincipal;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.ResponseEntity;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.authentication.logout.SecurityContextLogoutHandler;
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

	private final RegistrationService registrationService;

	private final AccountDeletionService accountDeletionService;

	private final PasswordEncoder passwordEncoder;

	public UserController(RegistrationService registrationService,
			AccountDeletionService accountDeletionService, PasswordEncoder passwordEncoder) {
		this.registrationService = registrationService;
		this.accountDeletionService = accountDeletionService;
		this.passwordEncoder = passwordEncoder;
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
	 * FR-019 — permanently erases the user and everything they own, then ends the session.
	 *
	 * <p>The password is re-verified against the hash already carried on the principal (no query:
	 * the session was established from that same hash). A mismatch throws
	 * {@code BadCredentialsException}, which Spring Security's {@code ExceptionTranslationFilter}
	 * turns into the same generic 401 a failed login returns.
	 */
	@DeleteMapping("/me")
	ResponseEntity<Void> deleteCurrentUser(@Valid @RequestBody DeleteAccountRequest request,
			@AuthenticationPrincipal UserPrincipal principal,
			HttpServletRequest httpRequest, HttpServletResponse httpResponse) {

		if (!passwordEncoder.matches(request.password(), principal.passwordHash())) {
			throw new BadCredentialsException("Re-authentication failed");
		}
		accountDeletionService.deleteAccount(principal.userId());

		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		new SecurityContextLogoutHandler().logout(httpRequest, httpResponse, authentication);
		return ResponseEntity.noContent().build();
	}
}
