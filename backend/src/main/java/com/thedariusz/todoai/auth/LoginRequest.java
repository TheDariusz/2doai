package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.NotBlank;

/**
 * Credentials for {@code POST /api/sessions} (the {@code Credentials} schema in
 * {@code openapi.yaml}).
 *
 * <p>Deliberately looser than {@link RegisterRequest}: only {@code @NotBlank}, no {@code @Email} or
 * min-length. Login must not tell an attacker which submissions are <em>shaped</em> like a real
 * account — every wrong credential, malformed or not, deserves the same generic 401.
 */
public record LoginRequest(

		@NotBlank
		String email,

		@NotBlank
		String password) {
}
