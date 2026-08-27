package com.thedariusz.todoai.account;

import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.ai.memory.AiMemory;
import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import com.thedariusz.todoai.auth.RegistrationService;
import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalHorizon;
import com.thedariusz.todoai.goal.GoalLayer;
import com.thedariusz.todoai.goal.GoalRepository;
import com.thedariusz.todoai.proposal.Proposal;
import com.thedariusz.todoai.proposal.ProposalRepository;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

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
	private GoalRepository goals;

	@Autowired
	private ProposalRepository proposals;

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
	 * The one table the FK backstop above does <b>not</b> protect. {@code proposal.goal_id} carries
	 * {@code ON DELETE CASCADE}, so {@code GoalDataDeleter} takes the proposals with it and a
	 * forgotten {@code ProposalDataDeleter} would still let the account delete succeed — silently, and
	 * only for as long as deleter ordering happens to favour it. So FR-019 is asserted directly here:
	 * with a proposal on file, deleting the account leaves no row behind.
	 */
	@Test
	void deletingAnAccountRemovesItsProposalsToo() {
		User alice = registrationService.register(uniqueEmail(), "correct-horse");
		User bob = registrationService.register(uniqueEmail(), "correct-horse");
		givenPendingProposal(alice.getId());
		givenPendingProposal(bob.getId());

		accountDeletionService.deleteAccount(alice.getId());

		assertThat(proposalRowsOf(alice.getId())).isZero();
		assertThat(proposalRowsOf(bob.getId())).isEqualTo(1);
	}

	/**
	 * {@code deleteById} has been a silent no-op for a missing row since Spring Data 3.0, so without
	 * this guard a stale session's deletion request erases nothing and still reports success.
	 */
	@Test
	void refusesToReportSuccessWhenThereIsNoSuchAccount() {
		assertThatThrownBy(() -> accountDeletionService.deleteAccount(UUID.randomUUID()))
				.isInstanceOf(IllegalStateException.class);
	}

	/**
	 * The guard that makes a forgotten deleter impossible to ship silently — asserted against the
	 * <b>schema</b> rather than against the deleter registry.
	 *
	 * <p>A registry check could only prove that some deleter claimed a table name; it could not prove
	 * the deleter deletes anything, and it would pass for a stub. What actually protects the data is
	 * the database: every {@code user_id}-bearing table must carry a foreign key to {@code app_user}
	 * with no {@code ON DELETE} action, so a missing or broken deleter makes the final user delete
	 * fail loudly on the constraint instead of orphaning personal data. That is a property the
	 * migration either has or does not, and it holds in production, not merely in CI.
	 */
	@Test
	void everyUserScopedTableIsProtectedByARestrictingForeignKey() {
		List<String> unprotected = jdbc.queryForList("""
				SELECT c.table_name
				FROM information_schema.columns c
				WHERE c.table_schema = 'public'
				  AND c.column_name = 'user_id'
				  AND NOT EXISTS (
				      SELECT 1
				      FROM information_schema.table_constraints tc
				      JOIN information_schema.key_column_usage kcu
				           ON kcu.constraint_name = tc.constraint_name
				          AND kcu.constraint_schema = tc.constraint_schema
				      JOIN information_schema.referential_constraints rc
				           ON rc.constraint_name = tc.constraint_name
				          AND rc.constraint_schema = tc.constraint_schema
				      JOIN information_schema.constraint_column_usage ccu
				           ON ccu.constraint_name = tc.constraint_name
				          AND ccu.constraint_schema = tc.constraint_schema
				      WHERE tc.constraint_type = 'FOREIGN KEY'
				        AND tc.table_schema = c.table_schema
				        AND tc.table_name = c.table_name
				        AND kcu.column_name = c.column_name
				        AND ccu.table_name = 'app_user'
				        AND rc.delete_rule = 'NO ACTION'
				  )
				""", String.class);

		assertThat(unprotected)
				.as("user_id tables with no restricting FK to app_user — a forgotten deleter would "
						+ "silently orphan their rows instead of failing the account deletion")
				.isEmpty();
	}

	/** The schema is genuinely being inspected — a query that matched nothing would pass vacuously. */
	@Test
	void theSchemaActuallyHasUserScopedTablesToProtect() {
		List<String> userScopedTables = jdbc.queryForList("""
				SELECT table_name FROM information_schema.columns
				WHERE table_schema = 'public' AND column_name = 'user_id'
				""", String.class);

		assertThat(userScopedTables).contains("ai_memory");
	}

	/**
	 * The backstop itself, asserted rather than assumed: with a child row still present, deleting the
	 * user must fail. This is the claim the whole {@link PerUserDataDeleter} design rests on — that
	 * forgetting a deleter is loud — and it was previously only stated in prose.
	 */
	@Test
	void deletingAUserWithDataLeftBehindFailsOnTheForeignKey() {
		User orphaned = registrationService.register(uniqueEmail(), "correct-horse");

		assertThatThrownBy(() -> {
			users.deleteById(orphaned.getId());
			jdbc.execute("SELECT 1");
			users.flush();
		}).isInstanceOf(DataIntegrityViolationException.class);
	}

	private void givenPendingProposal(UUID userId) {
		UUID goalId = goals.saveAndFlush(new Goal(userId, "Wrócić na rower", GoalLayer.GOAL,
				GoalHorizon.THIS_YEAR, null, LifeDomain.HEALTH)).getId();
		proposals.saveAndFlush(
				new Proposal(userId, goalId, "Wracamy do tego?", 21, Proposal.Source.TEMPLATE));
	}

	private int proposalRowsOf(UUID userId) {
		return jdbc.queryForObject(
				"SELECT count(*) FROM proposal WHERE user_id = ?", Integer.class, userId);
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
