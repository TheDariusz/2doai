package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Credentials for {@code POST /api/sessions} (the {@code Credentials} schema in
 * {@code openapi.yaml}).
 *
 * <p>Deliberately looser than {@link RegisterRequest}: no {@code @Email} and no minimum length. Login
 * must not tell an attacker which syntactically plausible submissions belong to a real account —
 * they all receive the same generic 401.
 *
 * <p>The two size bounds reveal nothing about accounts and are kept. The 72-byte password ceiling
 * mirrors registration, so an over-long password fails the same way on both endpoints rather than
 * silently never matching ({@code PasswordEncoder.matches} does not reject it, it just returns
 * false). The 320-character email bound stops an unbounded string from being copied, lowercased and
 * shipped to the metered database for a value no {@code VARCHAR(320)} column could hold.
 */
public record LoginRequest(

		@NotBlank
		@Size(max = 320)
		String email,

		@NotBlank
		@Utf8ByteLength(max = Utf8ByteLength.BCRYPT_MAX_PASSWORD_BYTES)
		String password) {
}
