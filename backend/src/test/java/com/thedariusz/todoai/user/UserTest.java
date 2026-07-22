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
		User user = new User(Email.of("Alice@Example.com"), "{bcrypt}$2a$10$hash");

		assertThat(user.getEmail()).isEqualTo("alice@example.com");
		assertThat(user.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$hash");
	}

	@Test
	void rejectsNullEmail() {
		assertThatThrownBy(() -> new User(null, "{bcrypt}$2a$10$hash"))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void rejectsBlankPasswordHash() {
		assertThatThrownBy(() -> new User(Email.of("alice@example.com"), "   "))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
