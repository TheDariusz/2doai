package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The code a user typed, for {@code POST /api/verifications} (the {@code EmailVerification} schema
 * in {@code openapi.yaml}).
 *
 * <p>The address is validated as loosely as {@link LoginRequest}'s and for the same reason: this
 * endpoint answers identically for every address it does not recognise, and a stricter constraint
 * here would start answering 422 for some of them and 403 for others.
 *
 * <p>The code's shape, on the other hand, is the contract — six digits, nothing else — so a
 * malformed one is a 422 and never spends one of the five guesses the real code is worth.
 */
public record EmailVerification(

		@NotBlank
		@Size(max = 320)
		String email,

		@NotBlank
		@Pattern(regexp = "\\d{6}")
		String code) {
}
