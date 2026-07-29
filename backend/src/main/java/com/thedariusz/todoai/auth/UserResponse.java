package com.thedariusz.todoai.auth;

import java.util.UUID;

import com.thedariusz.todoai.security.UserPrincipal;
import com.thedariusz.todoai.user.User;

/**
 * The {@code User} schema from {@code openapi.yaml} — the body of register, login, and
 * {@code GET /api/users/me}. Minimal by design (YAGNI): identity only, never the password hash.
 */
public record UserResponse(UUID id, String email) {

	public static UserResponse from(User user) {
		return new UserResponse(user.getId(), user.getEmail());
	}

	/**
	 * Built straight from the authenticated principal — no database round-trip. The principal
	 * already carries both fields, and skipping the query matters here: {@code GET /api/users/me}
	 * is the SPA's bootstrap call, and every avoided query is idle time Neon can autosuspend
	 * through (see {@code context/foundation/lessons.md}).
	 */
	public static UserResponse from(UserPrincipal principal) {
		return new UserResponse(principal.userId(), principal.email());
	}
}
