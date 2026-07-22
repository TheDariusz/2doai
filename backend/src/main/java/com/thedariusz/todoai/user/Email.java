package com.thedariusz.todoai.user;

import java.util.Locale;
import java.util.Objects;

import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import jakarta.validation.constraints.NotBlank;

/**
 * Email value object — the account's login identity. Centralizes the "what is a valid email"
 * invariant at the domain boundary so it can't drift between the auth request DTOs (S-01
 * Phase 2, which declare {@code @Email}) and the {@link User} aggregate.
 *
 * <p>{@link #of(String)} strips surrounding whitespace and lowercases (locale-independent, so
 * the Turkish-I trap can't bite), then validates the normalized form against the <em>same</em>
 * Jakarta {@code @Email} + {@code @NotBlank} constraints the DTOs use — reusing the library
 * validator rather than a hand-rolled regex (see the {@code prefer-apache-commons-helpers}
 * preference: Jakarta Bean Validation for domain validation). Case-folding here is what makes
 * the {@code app_user.email} UNIQUE index behave as a case-insensitive account key.
 *
 * <p>Normalization + validation live in the compact canonical constructor, so <em>every</em>
 * construction path — {@link #of(String)} or {@code new Email(...)} — yields a normalized,
 * validated value; there is no way to build an un-normalized or unvalidated one.
 */
public record Email(String value) {

	// Shared, thread-safe Validator so construction doesn't rebuild a factory per call. Held for
	// the app lifetime (never closed) — the standard singleton pattern for a Bean Validation provider.
	private static final ValidatorFactory VALIDATOR_FACTORY = Validation.buildDefaultValidatorFactory();
	private static final Validator VALIDATOR = VALIDATOR_FACTORY.getValidator();

	/**
	 * Normalize (strip + lowercase) then validate. Rejects {@code null} with
	 * {@link NullPointerException} (mirrors the aggregate identity guards) and a malformed or blank
	 * address with {@link IllegalArgumentException}.
	 */
	public Email {
		Objects.requireNonNull(value, "email");
		value = value.strip().toLowerCase(Locale.ROOT);
		if (!VALIDATOR.validate(new Candidate(value)).isEmpty()) {
			throw new IllegalArgumentException("Malformed email address");
		}
	}

	/** Preferred factory — reads as {@code Email.of("a@b.com")} at call sites. */
	public static Email of(String raw) {
		return new Email(raw);
	}

	// Fully-qualified @Email: the simple name Email is taken by the enclosing record, so the
	// annotation must be referenced by FQN here.
	private record Candidate(@NotBlank @jakarta.validation.constraints.Email String value) {
	}
}
