package com.thedariusz.todoai.security;

import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/**
 * The single seam every per-user query obtains the authenticated user id through — the
 * enforcement point of the query-scoping isolation contract (see {@link UserOwned}). Callers scope
 * their reads/writes with {@code requireId()} rather than trusting a client-supplied id, so an
 * authenticated request can only ever touch its own rows.
 *
 * <p>Reads the id straight from the {@code SecurityContextHolder} (populated per request from the
 * session by the Spring Security filter chain) — no DB round-trip, keeping session validation off
 * the metered Neon database (the idleness cost rule in {@code lessons.md}).
 */
@Component
public class CurrentUser {

	/**
	 * @return the authenticated user's id
	 * @throws AuthenticationCredentialsNotFoundException when there is no authenticated
	 *         {@link UserPrincipal} in the security context — an {@code AuthenticationException},
	 *         so the {@code ExceptionTranslationFilter} maps it to 401 via the entry point.
	 */
	public UUID requireId() {
		Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
		if (authentication == null
				|| !authentication.isAuthenticated()
				|| !(authentication.getPrincipal() instanceof UserPrincipal principal)) {
			throw new AuthenticationCredentialsNotFoundException("No authenticated user");
		}
		return principal.userId();
	}
}
