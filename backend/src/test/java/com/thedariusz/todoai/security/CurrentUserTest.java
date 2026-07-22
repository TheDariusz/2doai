package com.thedariusz.todoai.security;

import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link CurrentUser} isolation seam: it yields the authenticated user's id
 * from the security context, and fails with an {@code AuthenticationException} (→ 401) when there
 * is no authenticated {@link UserPrincipal} — so no per-user query can run without an owner.
 */
class CurrentUserTest {

	private final CurrentUser currentUser = new CurrentUser();

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	@Test
	void returnsAuthenticatedUserId() {
		UUID userId = UUID.randomUUID();
		UserPrincipal principal = new UserPrincipal(userId, "alice@example.com", "{bcrypt}$2a$10$hash");
		SecurityContextHolder.getContext().setAuthentication(
				new UsernamePasswordAuthenticationToken(principal, null, principal.getAuthorities()));

		assertThat(currentUser.requireId()).isEqualTo(userId);
	}

	@Test
	void throwsWhenUnauthenticated() {
		assertThatThrownBy(currentUser::requireId)
				.isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}
}
