package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * "Send the code again", for {@code POST /api/verification-codes} (the
 * {@code VerificationCodeRequest} schema in {@code openapi.yaml}). Address only — which account it
 * belongs to, and whether it has one at all, is deliberately not something the answer reveals.
 */
public record VerificationCodeRequest(

		@NotBlank
		@Size(max = 320)
		String email) {
}
