package com.thedariusz.todoai.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link User} aggregate's construction invariants — no user without a valid
 * email or a non-blank password hash, and the raw password never entering the domain (the
 * constructor only ever receives an already-encoded hash).
 */
class UserTest {

	@Test
	void storesNormalizedEmailAndHash() {
		User user = new User(Email.of("Alice@Example.com"), "{bcrypt}$2a$10$hash", AppLanguage.EN);

		assertThat(user.getEmail()).isEqualTo("alice@example.com");
		assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$hash");
		assertThat(user.getLanguage()).isEqualTo(AppLanguage.EN);
	}

	/**
	 * FR-003 — the account starts in the language its sign-up screen was shown in, so the language
	 * is an identity invariant like the email: there is no such thing as a user without one. The
	 * only null this field ever holds comes from a row written before the column existed, which
	 * JPA hydrates through the no-arg constructor and {@link User#getLanguage()} reads as Polish.
	 */
	@Test
	void rejectsNullLanguage() {
		assertThatThrownBy(() -> new User(Email.of("alice@example.com"), "{bcrypt}$2a$10$hash", null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void rejectsNullEmail() {
		assertThatThrownBy(() -> new User(null, "{bcrypt}$2a$10$hash", AppLanguage.EN))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void rejectsBlankPasswordHash() {
		assertThatThrownBy(() -> new User(Email.of("alice@example.com"), "   ", AppLanguage.EN))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
