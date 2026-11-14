package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;

/**
 * Credentials for {@code POST /api/sessions} (the {@code Credentials} schema in
 * {@code openapi.yaml}): an address and <b>exactly one</b> of a password or a mailed code. Both, or
 * neither, is a malformed request (422), never a guess.
 *
 * <p>Deliberately looser than {@link RegisterRequest}: no {@code @Email} and no minimum length. Login
 * must not tell an attacker which syntactically plausible submissions belong to a real account —
 * they all receive the same generic 401.
 *
 * <p>The size bounds reveal nothing about accounts and are kept. The 72-byte password ceiling mirrors
 * registration, so an over-long password fails the same way on both endpoints rather than silently
 * never matching ({@code PasswordEncoder.matches} does not reject it, it just returns false). The
 * 320-character email bound stops an unbounded string from being copied, lowercased and shipped to the
 * metered database for a value no {@code VARCHAR(320)} column could hold. The code's shape is the
 * contract — {@link EmailVerificationService#CODE_LENGTH} digits — so a malformed one is a 422 and never
 * spends one of the guesses the real code is worth.
 */
public record LoginRequest(

		@NotBlank
		@Size(max = 320)
		String email,

		@Utf8ByteLength
		String password,

		@Pattern(regexp = "\\d{" + EmailVerificationService.CODE_LENGTH + "}")
		String code) {

	/** Whether this sign-in presents a code (and so no password). */
	public boolean signsInByCode() {
		return code != null;
	}

	/**
	 * One credential, never two and never none. A blank password counts as none, as {@code @NotBlank}
	 * made it before the code existed, so {@code {"email": …, "password": ""}} stays a 422.
	 */
	@AssertTrue
	public boolean isOneCredential() {
		return StringUtils.isNotBlank(password) != (code != null);
	}
}
