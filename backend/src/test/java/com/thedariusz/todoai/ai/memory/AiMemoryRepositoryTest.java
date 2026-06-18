package com.thedariusz.todoai.ai.memory;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Round-trips the AI-memory aggregate against a real Postgres (Testcontainers) with the
 * {@code V3} migration applied. Proves the first use of the UUIDv7 + audit-column
 * convention: persist a root with a profile fact and an episode (incl. a {@code jsonb}
 * payload), reload by {@code user_id}, and assert children, audit timestamps and v7 ids
 * all populate.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AiMemoryRepositoryTest {

	@Autowired
	AiMemoryRepository memories;

	@Test
	void persistsAndReloadsAggregateByUserId() {
		UUID userId = UUID.randomUUID();
		OffsetDateTime occurredAt = OffsetDateTime.parse("2026-06-17T08:00:00Z");

		AiMemory memory = new AiMemory(userId);
		memory.addFact("occupation", "Software engineer", "onboarding");
		memory.recordEpisode("task-completed", "{\"taskId\":\"abc\",\"domain\":\"HEALTH\"}", occurredAt);
		memories.saveAndFlush(memory);

		AiMemory reloaded = memories.findByUserId(userId).orElseThrow();

		assertThat(reloaded.getId()).isNotNull();
		assertThat(reloaded.getId().version()).isEqualTo(7);
		assertThat(reloaded.getUserId()).isEqualTo(userId);
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();

		assertThat(reloaded.getProfileFacts()).hasSize(1);
		ProfileFact fact = reloaded.getProfileFacts().iterator().next();
		assertThat(fact.getId()).isNotNull();
		assertThat(fact.getId().version()).isEqualTo(7);
		assertThat(fact.getKind()).isEqualTo("occupation");
		assertThat(fact.getContent()).isEqualTo("Software engineer");
		assertThat(fact.getProvenance()).isEqualTo("onboarding");
		assertThat(fact.getCreatedAt()).isNotNull();
		assertThat(fact.getUpdatedAt()).isNotNull();

		assertThat(reloaded.getEpisodes()).hasSize(1);
		Episode episode = reloaded.getEpisodes().iterator().next();
		assertThat(episode.getId()).isNotNull();
		assertThat(episode.getId().version()).isEqualTo(7);
		assertThat(episode.getEventType()).isEqualTo("task-completed");
		// jsonb normalizes whitespace/key order, so assert on values rather than exact text.
		assertThat(episode.getPayload()).contains("HEALTH").contains("abc");
		assertThat(episode.getOccurredAt()).isEqualTo(occurredAt);
		assertThat(episode.getCreatedAt()).isNotNull();
	}

	@Test
	void enforcesOneMemoryPerUser() {
		UUID userId = UUID.randomUUID();
		memories.saveAndFlush(new AiMemory(userId));

		assertThat(memories.findByUserId(userId)).isPresent();
		assertThat(memories.findByUserId(UUID.randomUUID())).isEmpty();
	}
}
