package com.thedariusz.todoai.auth;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * The code a user typed, for {@code POST /api/verifications} (the {@code EmailVerification} schema
 * in {@code openapi.yaml} — the wire name stays, this is the class that carries it).
 *
 * <p>The address is validated as loosely as {@link LoginRequest}'s and for the same reason: this
 * endpoint answers identically for every address it does not recognise, and a stricter constraint
 * here would start answering 422 for some of them and 403 for others.
 *
 * <p>The code's shape, on the other hand, is the contract — {@link EmailVerificationService#CODE_LENGTH}
 * digits, nothing else — so a malformed one is a 422 and never spends one of the guesses the real code
 * is worth. The width is interpolated rather than typed: a constant expression is legal in an
 * annotation, and this pattern rejecting every code the generator draws is the drift it prevents.
 */
public record EmailVerificationRequest(

		@NotBlank
		@Size(max = 320)
		String email,

		@NotBlank
		@Pattern(regexp = "\\d{" + EmailVerificationService.CODE_LENGTH + "}")
		String code) {
}
