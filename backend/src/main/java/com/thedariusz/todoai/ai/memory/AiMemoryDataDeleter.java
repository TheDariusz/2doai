package com.thedariusz.todoai.ai.memory;

import java.util.UUID;

import com.thedariusz.todoai.account.PerUserDataDeleter;
import org.springframework.stereotype.Component;

/**
 * FR-019 erasure of the AI-memory aggregate. Deleting the root is enough: {@link AiMemory} declares
 * {@code CascadeType.ALL} + {@code orphanRemoval} on both child collections, so Hibernate removes
 * the profile facts and the episodic log before the root — no {@code ON DELETE CASCADE} in the
 * schema, and no child-by-child deletion here.
 */
@Component
class AiMemoryDataDeleter implements PerUserDataDeleter {

	private final AiMemoryRepository memories;

	AiMemoryDataDeleter(AiMemoryRepository memories) {
		this.memories = memories;
	}

	@Override
	public void deleteAllForUser(UUID userId) {
		memories.findByUserId(userId).ifPresent(memories::delete);
	}

	@Override
	public String userScopedTable() {
		return "ai_memory";
	}
}
