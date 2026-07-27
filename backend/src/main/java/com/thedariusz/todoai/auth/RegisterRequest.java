package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Registration payload for {@code POST /api/users} (the {@code UserRegistration} schema in
 * {@code openapi.yaml}). Constraints mirror the contract: a syntactically valid address within the
 * {@code app_user.email} column width, and the baseline min-8 password.
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
		String password) {
}
