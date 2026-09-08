package com.thedariusz.todoai.user;

import java.time.OffsetDateTime;

import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link User} aggregate's construction invariants — no user without a valid
 * email or a non-blank password hash, and the raw password never entering the domain (the
 * constructor only ever receives an already-encoded hash) — and for the one rule the aggregate owns
 * about its own verification state.
 *
 * <p>That state is set with reflection rather than through setters, because the aggregate
 * deliberately has none: every one of those columns moves through a targeted update in
 * {@code UserRepository}, and leaving a mutator would leave a {@code save}-shaped resurrection bug
 * one call away. Reflection here is the price of that, and it is cheaper than the bug.
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

	/**
	 * The four fields that only mean anything together (DEV-51). A caller reading them one at a time is
	 * a caller who can get the conjunction wrong, which is why the rule is here and not in the service
	 * that spends the code.
	 */
	@Test
	void saysACodeMayBeTriedWhileItIsLiveAndUnspent() {
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(withCode(now.plusMinutes(15), 0).hasCodeAwaiting(now)).isTrue();
		assertThat(withCode(now.plusMinutes(15), User.MAX_VERIFICATION_ATTEMPTS - 1).hasCodeAwaiting(now))
				.as("the last guess the code is worth is still a guess")
				.isTrue();
	}

	@Test
	void refusesACodeThatIsMissingExpiredSpentOrAlreadyProved() {
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(new User(Email.of("alice@example.com"), "{bcrypt}$2a$10$hash", AppLanguage.EN)
				.hasCodeAwaiting(now)).as("a fresh account has no code drawn yet").isFalse();
		assertThat(withCode(now.minusSeconds(1), 0).hasCodeAwaiting(now)).as("expired").isFalse();
		assertThat(withCode(now.plusMinutes(15), User.MAX_VERIFICATION_ATTEMPTS).hasCodeAwaiting(now))
				.as("out of guesses").isFalse();

		User proved = withCode(now.plusMinutes(15), 0);
		ReflectionTestUtils.setField(proved, "emailVerifiedAt", now);
		assertThat(proved.hasCodeAwaiting(now)).as("already proved").isFalse();
	}

	private static User withCode(OffsetDateTime expiresAt, int attempts) {
		User user = new User(Email.of("alice@example.com"), "{bcrypt}$2a$10$hash", AppLanguage.EN);
		ReflectionTestUtils.setField(user, "verificationCodeHash", "{bcrypt}$2a$10$code");
		ReflectionTestUtils.setField(user, "verificationExpiresAt", expiresAt);
		ReflectionTestUtils.setField(user, "verificationAttempts", attempts);
		return user;
	}
}
