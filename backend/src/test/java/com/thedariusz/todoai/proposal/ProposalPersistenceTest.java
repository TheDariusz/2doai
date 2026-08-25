package com.thedariusz.todoai.proposal;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalHorizon;
import com.thedariusz.todoai.goal.GoalLayer;
import com.thedariusz.todoai.goal.GoalRepository;
import com.thedariusz.todoai.user.Email;
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
 * Round-trips the {@code proposal} aggregate against a real Postgres (Testcontainers) with {@code V8}
 * applied, and proves the two schema-level rules the Java cannot state on its own.
 *
 * <p><b>FR-018 is an index, not a service check.</b> "At most one pending proposal per user" is
 * enforced by a partial unique index on {@code (user_id) WHERE answered_at IS NULL}; a
 * service-level "is there one already?" would race with itself on a double-click and the database
 * would happily store both. So the rule is asserted here, at the only depth that actually holds it.
 *
 * <p><b>The cascade is asserted too</b>, because it is a knowingly weakened guard: {@code goal_id}
 * carries {@code ON DELETE CASCADE} so {@code DELETE /api/goals/{id}} keeps working with a proposal
 * pointing at the entry. Nothing else in the suite would notice if that clause went missing —
 * account deletion would still pass, because {@code GoalDataDeleter} runs first and takes the
 * proposals with it.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProposalPersistenceTest {

	@Autowired
	private ProposalRepository proposals;

	@Autowired
	private GoalRepository goals;

	@Autowired
	private UserRepository users;

	@Autowired
	private JdbcTemplate jdbc;

	@Test
	void persistsAndReloadsAProposalIncludingItsJsonbFirstStep() {
		UUID userId = persistedUserId();
		UUID goalId = persistedGoalId(userId);

		Proposal saved = proposals.saveAndFlush(pending(userId, goalId));
		saved.recordFirstStep("{\"steps\":[\"Wyjmij rower z piwnicy\",\"Napompuj koła\"]}");
		proposals.saveAndFlush(saved);

		Proposal reloaded = proposals.findByIdAndUserId(saved.getId(), userId).orElseThrow();

		assertThat(reloaded.getId().version()).isEqualTo(7);
		assertThat(reloaded.getUserId()).isEqualTo(userId);
		assertThat(reloaded.getGoalId()).isEqualTo(goalId);
		assertThat(reloaded.getMessage()).startsWith("Osiem miesięcy temu");
		assertThat(reloaded.getNeglectedDays()).isEqualTo(240);
		assertThat(reloaded.getSource()).isEqualTo(Proposal.Source.LLM);
		assertThat(reloaded.getFirstStep()).contains("Napompuj koła");
		assertThat(reloaded.getAnswer()).isNull();
		assertThat(reloaded.isPending()).isTrue();
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void refusesASecondPendingProposalForTheSameUser() {
		UUID userId = persistedUserId();
		UUID goalId = persistedGoalId(userId);
		proposals.saveAndFlush(pending(userId, goalId));

		assertThatThrownBy(() -> proposals.saveAndFlush(pending(userId, goalId)))
				.as("FR-018 holds at the schema, so two clicks landing at once cannot both persist")
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	@Test
	void letsEveryUserHaveOnePendingProposalOfTheirOwn() {
		UUID alice = persistedUserId();
		UUID bob = persistedUserId();

		proposals.saveAndFlush(pending(alice, persistedGoalId(alice)));
		proposals.saveAndFlush(pending(bob, persistedGoalId(bob)));

		assertThat(proposals.findByUserIdAndAnsweredAtIsNull(alice)).isPresent();
		assertThat(proposals.findByUserIdAndAnsweredAtIsNull(bob)).isPresent();
	}

	@Test
	void freesTheSlotOnceTheProposalIsAnswered() {
		UUID userId = persistedUserId();
		UUID goalId = persistedGoalId(userId);
		Proposal first = proposals.saveAndFlush(pending(userId, goalId));

		first.answer(ProposalAnswer.NOT_NOW, OffsetDateTime.now());
		proposals.saveAndFlush(first);
		Proposal second = proposals.saveAndFlush(pending(userId, goalId));

		assertThat(proposals.findByUserIdAndAnsweredAtIsNull(userId))
				.as("the index is partial on purpose — answered rows accumulate, pending ones do not")
				.get()
				.extracting(Proposal::getId)
				.isEqualTo(second.getId());
	}

	@Test
	void goesAwayWithTheEntryItPointsAt() {
		UUID userId = persistedUserId();
		UUID goalId = persistedGoalId(userId);
		proposals.saveAndFlush(pending(userId, goalId));

		// Straight through JDBC: the claim under test is the FK clause itself, and going through the
		// repository would only add a transaction of its own to prove the same DELETE.
		jdbc.update("DELETE FROM goal WHERE id = ?", goalId);

		assertThat(jdbc.queryForObject("SELECT count(*) FROM proposal WHERE goal_id = ?", Integer.class,
				goalId))
				.as("without ON DELETE CASCADE, deleting an entry that carries a proposal would fail "
						+ "on the foreign key and DELETE /api/goals/{id} would start returning 500")
				.isZero();
	}

	@Test
	void scopesReadsToTheOwner() {
		UUID alice = persistedUserId();
		UUID bob = persistedUserId();
		Proposal hers = proposals.saveAndFlush(pending(alice, persistedGoalId(alice)));

		assertThat(proposals.findByIdAndUserId(hers.getId(), bob)).isEmpty();
		assertThat(proposals.findByUserIdAndAnsweredAtIsNull(bob)).isEmpty();
	}

	private static Proposal pending(UUID userId, UUID goalId) {
		return new Proposal(userId, goalId,
				"Osiem miesięcy temu wpisałeś „Wrócić na rower” — chcesz do tego wrócić?", 240,
				Proposal.Source.LLM);
	}

	private UUID persistedUserId() {
		return users.saveAndFlush(new User(Email.of("owner-" + UUID.randomUUID() + "@example.com"),
				"{bcrypt}$2a$10$hash")).getId();
	}

	private UUID persistedGoalId(UUID userId) {
		return goals.saveAndFlush(new Goal(userId, "Wrócić na rower", GoalLayer.GOAL,
				GoalHorizon.THIS_YEAR, null, LifeDomain.HEALTH)).getId();
	}
}
