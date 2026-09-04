package com.thedariusz.todoai.auth;

import java.util.UUID;

import com.thedariusz.todoai.security.UserPrincipal;
import com.thedariusz.todoai.user.AppLanguage;
import com.thedariusz.todoai.user.User;

/**
 * The {@code User} schema from {@code openapi.yaml} — the body of register, login, and
 * {@code GET /api/users/me}. Minimal by design (YAGNI): identity only, never the password hash.
 *
 * <p>{@code language} is what the SPA seeds itself from at login: before it, the browser decides
 * what the auth screens are rendered in; after it, the account does, and this field is how the SPA
 * learns which (FR-002/FR-004).
 */
public record UserResponse(UUID id, String email, AppLanguage language) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail(), user.getLanguage());
	}

	/**
	 * Built straight from the authenticated principal — no database round-trip. The principal
	 * already carries both fields, and skipping the query matters here: {@code GET /api/users/me}
	 * is the SPA's bootstrap call, and every avoided query is idle time Neon can autosuspend
	 * through (see {@code context/foundation/lessons.md}).
	 */
	public static UserResponse from(UserPrincipal principal) {
		return new UserResponse(principal.userId(), principal.email(), principal.language());
	}
}
