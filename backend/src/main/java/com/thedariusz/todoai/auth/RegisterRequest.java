package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload for {@code POST /api/users} (the {@code UserRegistration} schema in
 * {@code openapi.yaml}). Constraints mirror the contract: a syntactically valid address within the
 * {@code app_user.email} column width, and a password between the baseline eight-character minimum
 * and BCrypt's 72-UTF-8-byte maximum.
 *
 * <p>Validation failures surface as <b>422</b> Problem JSON via {@code ApiExceptionHandler} — the
 * raw password never reaches the domain, only the encoded hash does.
 */
public record RegisterRequest(

		@NotBlank
		@Email
		@Size(max = 320)
		String email,

		@NotBlank
		@Size(min = 8)
		@Utf8ByteLength(max = 72)
		String password) {

	public RegisterRequest {
		// The API contract accepts copy-pasted addresses with surrounding whitespace. Normalize
		// before Bean Validation evaluates @Email; the Email value object lowercases in the domain.
		if (email != null) {
			email = email.strip();
		}
	}
}
