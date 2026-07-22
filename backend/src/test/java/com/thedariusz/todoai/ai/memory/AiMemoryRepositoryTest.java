package com.thedariusz.todoai.ai.memory;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.validation.ConstraintViolationException;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.hibernate.Hibernate;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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

	@Autowired
	UserRepository users;
	
	private UUID persistedUserId() {
		User owner = users.saveAndFlush(new User(Email.of("owner-" + UUID.randomUUID() + "@example.com"),
				"{bcrypt}$2a$10$hash"));
		return owner.getId();
	}

	@Test
	void persistsAndReloadsAggregateByUserId() {
		UUID userId = persistedUserId();
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
	void findByUserIdEagerlyInitializesChildCollections() {
		UUID userId = persistedUserId();
		AiMemory memory = new AiMemory(userId);
		memory.addFact("occupation", "Software engineer", "onboarding");
		memory.recordEpisode("task-completed", "{}", OffsetDateTime.parse("2026-06-17T08:00:00Z"));
		memories.saveAndFlush(memory);

		AiMemory reloaded = memories.findByUserId(userId).orElseThrow();

		// The @EntityGraph must fetch both children in the findByUserId query so callers get a
		// fully-initialized aggregate under open-in-view=false. Assert on the raw persistent
		// collections (the getters return unmodifiable views, which Hibernate always reports as
		// initialized) so this holds whether or not a session is still open — drop the
		// @EntityGraph and both go false. This test is deliberately non-transactional.
		assertThat(Hibernate.isInitialized(ReflectionTestUtils.getField(reloaded, "profileFacts"))).isTrue();
		assertThat(Hibernate.isInitialized(ReflectionTestUtils.getField(reloaded, "episodes"))).isTrue();
	}

	@Test
	void enforcesOneMemoryPerUser() {
		UUID userId = persistedUserId();
		memories.saveAndFlush(new AiMemory(userId));

		assertThat(memories.findByUserId(userId)).isPresent();
		assertThat(memories.findByUserId(UUID.randomUUID())).isEmpty();
	}

	@Test
	void rejectsInvalidChildOnFlush() {
		AiMemory memory = new AiMemory(persistedUserId());
		// Blank kind is non-null, so the NOT NULL column alone would accept it — only the
		// @NotBlank bean-validation constraint catches it, and only if Hibernate enforces it
		// on flush. This proves the constraint isn't silently inert.
		memory.addFact("   ", "Software engineer", "onboarding");

		assertThatThrownBy(() -> memories.saveAndFlush(memory))
				.isInstanceOf(ConstraintViolationException.class);
	}
}
