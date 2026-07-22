package com.thedariusz.todoai.user;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Email} value object — the identity invariant, independent of any
 * database or Spring context. Proves normalization (strip + case-fold) and rejection of malformed
 * input, so the {@code app_user.email} UNIQUE index behaves as a case-insensitive account key.
 */
class EmailTest {

	@Test
	void lowercasesAndTrims() {
		assertThat(Email.of("  Alice@Example.COM  ").value()).isEqualTo("alice@example.com");
	}

	@Test
	void isValueBasedOnNormalizedForm() {
		// Two differently-cased/padded inputs normalize to the same value → equal VOs.
		assertThat(Email.of("USER@Example.com")).isEqualTo(Email.of("user@example.com"));
	}

	@Test
	void rejectsNull() {
		assertThatThrownBy(() -> Email.of(null))
				.isInstanceOf(NullPointerException.class);
	}

	@Test
	void rejectsBlank() {
		assertThatThrownBy(() -> Email.of("   "))
				.isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	void rejectsMalformed() {
		assertThatThrownBy(() -> Email.of("not-an-email"))
				.isInstanceOf(IllegalArgumentException.class);
	}
}
