package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials for {@code POST /api/sessions} (the {@code Credentials} schema in
 * {@code openapi.yaml}).
 *
 * <p>Deliberately looser than {@link RegisterRequest}: no {@code @Email} or minimum length. Login
 * must not tell an attacker which syntactically plausible submissions belong to a real account —
 * they all receive the same generic 401. The 72-byte maximum is implementation safety and returns
 * 422 before authentication because BCrypt cannot accept a longer password.
 */
public record LoginRequest(

		@NotBlank
		String email,

		@NotBlank
		@Utf8ByteLength(max = 72)
		String password) {
}
