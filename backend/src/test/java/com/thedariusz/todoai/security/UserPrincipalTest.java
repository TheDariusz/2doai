package com.thedariusz.todoai.security;

import java.util.UUID;

import com.thedariusz.todoai.user.AppLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The record's own invariants. Everything about how it <em>behaves</em> in a session — identity
 * equality surviving a language switch, the hash staying out of {@code toString} — is pinned
 * end-to-end by {@code AuthApiTest}; what is left here is the state it refuses to be built in.
 */
class UserPrincipalTest {

	/**
	 * The id is the one component whose absence would surface somewhere else entirely: {@code equals}
	 * dereferences it, so a null would throw from inside a {@code SessionRegistry} lookup — during
	 * an account deletion sweeping other devices — rather than where the principal was built.
	 */
	@Test
	void refusesToExistWithoutTheIdEverythingIsAuthorizedAgainst() {
		assertThatThrownBy(() -> new UserPrincipal(null, "ala@example.pl", "{bcrypt}$2a$10$hash", AppLanguage.PL, true))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("userId");
	}

	/** FR-002 moves this one mid-session, which is the reason it is worth stating it cannot be dropped. */
	@Test
	void refusesToExistWithoutALanguage() {
		assertThatThrownBy(() -> new UserPrincipal(UUID.randomUUID(), "ala@example.pl", "{bcrypt}$2a$10$hash", null, true))
				.isInstanceOf(NullPointerException.class)
				.hasMessage("language");
	}
}
