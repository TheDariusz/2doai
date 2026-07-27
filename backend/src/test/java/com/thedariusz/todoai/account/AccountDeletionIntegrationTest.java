package com.thedariusz.todoai.account;

import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.ai.memory.AiMemory;
import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import com.thedariusz.todoai.auth.RegistrationService;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two properties that make per-user data trustworthy, proven against a real Postgres:
 * <b>isolation</b> (a user's scoped finder never returns anyone else's aggregate) and <b>FR-019
 * completeness</b> (deleting an account leaves nothing of it behind — and nothing of anybody else
 * missing). Both are cheap to assert today with one per-user aggregate, and are the regression net
 * for every aggregate S-02+ adds.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class AccountDeletionIntegrationTest {

	@Autowired
	private RegistrationService registrationService;

	@Autowired
	private AccountDeletionService accountDeletionService;

	@Autowired
	private UserRepository users;

	@Autowired
	private AiMemoryRepository memories;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void scopedFinderReturnsOnlyTheOwnersMemory() {
		User alice = registrationService.register(uniqueEmail(), "correct-horse");
		User bob = registrationService.register(uniqueEmail(), "correct-horse");

		AiMemory aliceMemory = memories.findByUserId(alice.getId()).orElseThrow();
		AiMemory bobMemory = memories.findByUserId(bob.getId()).orElseThrow();

		assertThat(aliceMemory.getUserId()).isEqualTo(alice.getId());
		assertThat(bobMemory.getId()).isNotEqualTo(aliceMemory.getId());
	}

	@Test
	void registrationProvisionsExactlyOneMemoryPerUser() {
		User user = registrationService.register(uniqueEmail(), "correct-horse");

		assertThat(memories.findByUserId(user.getId())).isPresent();
	}

	@Test
	void deletingAnAccountRemovesTheUserTheMemoryAndItsChildren() {
		User alice = registrationService.register(uniqueEmail(), "correct-horse");
		User bob = registrationService.register(uniqueEmail(), "correct-horse");
		UUID aliceMemoryId = givenMemoryWithChildren(alice.getId());
		UUID bobMemoryId = givenMemoryWithChildren(bob.getId());

		accountDeletionService.deleteAccount(alice.getId());

		assertThat(users.findById(alice.getId())).isEmpty();
		assertThat(memories.findByUserId(alice.getId())).isEmpty();
		assertThat(childRowsOf(aliceMemoryId)).isZero();

		// Bob is untouched — deletion is scoped, not a sweep.
		assertThat(users.findById(bob.getId())).isPresent();
		assertThat(childRowsOf(bobMemoryId)).isEqualTo(2);
	}

	/**
	 * The guard that makes a forgotten deleter impossible to ship silently: every {@code user_id}
	 * -bearing table in the live schema must have a registered {@link PerUserDataDeleter}. It reads
	 * the schema rather than a hand-maintained list precisely so that adding a table to a migration
	 * — and forgetting the deleter — fails here rather than leaving orphaned personal data.
	 */
	@Test
	void everyUserScopedTableHasARegisteredDeleter() {
		List<String> userScopedTables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.columns
				WHERE table_schema = 'public' AND column_name = 'user_id'
				""", String.class);

		assertThat(userScopedTables).isNotEmpty();
		assertThat(accountDeletionService.registeredTables()).containsAll(userScopedTables);
	}

	private UUID givenMemoryWithChildren(UUID userId) {
		AiMemory memory = memories.findByUserId(userId).orElseThrow();
		memory.addFact("VALUE", "prefers mornings", "onboarding");
		memory.recordEpisode("TASK_COMPLETED", "{}", java.time.OffsetDateTime.now());
		return memories.save(memory).getId();
	}

	private int childRowsOf(UUID memoryId) {
		Integer facts = jdbc.queryForObject(
				"SELECT count(*) FROM ai_memory_profile_fact WHERE ai_memory_id = ?", Integer.class, memoryId);
		Integer episodes = jdbc.queryForObject(
				"SELECT count(*) FROM ai_memory_episode WHERE ai_memory_id = ?", Integer.class, memoryId);
		return facts + episodes;
	}

	private static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}
}
