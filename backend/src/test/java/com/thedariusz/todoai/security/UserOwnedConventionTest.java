package com.thedariusz.todoai.security;

import java.lang.reflect.Method;
import java.util.UUID;

import com.thedariusz.todoai.ai.memory.AiMemory;
import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Guards the per-user isolation convention S-01 establishes while {@code AiMemory} is the only
 * per-user aggregate (N = 1): every per-user aggregate implements {@link UserOwned} and is read
 * through a {@link CurrentUser}-scoped finder ({@code findByUserId}), never by a client-supplied
 * id. When S-02 adds the second per-user entity, this convention is promoted to a structural
 * ArchUnit rule (out of scope in S-01).
 */
class UserOwnedConventionTest {

	@Test
	void aiMemoryIsUserOwned() {
		assertThat(UserOwned.class).isAssignableFrom(AiMemory.class);
	}

	@Test
	void userOwnedExposesTheOwnerId() {
		UUID userId = UUID.randomUUID();
		UserOwned owned = new AiMemory(userId);

		assertThat(owned.getUserId()).isEqualTo(userId);
	}

	@Test
	void aiMemoryIsReadThroughAUserScopedFinder() throws NoSuchMethodException {
		// The scoped-access convention: per-user reads go through findByUserId(UUID) — the finder a
		// future ArchUnit/RLS guard keys off — not findById on a client-supplied aggregate id.
		Method finder = AiMemoryRepository.class.getMethod("findByUserId", UUID.class);

		assertThat(finder.getReturnType()).isEqualTo(java.util.Optional.class);
	}
}
