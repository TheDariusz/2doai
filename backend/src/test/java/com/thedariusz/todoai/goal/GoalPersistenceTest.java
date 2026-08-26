package com.thedariusz.todoai.goal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.category.LifeDomain;
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
import static org.assertj.core.api.Assertions.tuple;

/**
 * Round-trips the {@code goal} aggregate against a real Postgres (Testcontainers) with the
 * {@code V6} and {@code V7} migrations applied. One table carries all three layers — a long-term
 * GOAL (horizon required, no term), a someday DREAM (neither) and a current TASK (optional term, no
 * horizon) — so the mapping proof has to cover the discriminator, both nullable time fields, and the
 * optional {@code category_code} FK.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class GoalPersistenceTest {

	@Autowired
	GoalRepository goals;

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	private void insertRawGoal(UUID userId, String layer, String horizon, String categoryCode) {
		insertRawGoal(userId, layer, horizon, categoryCode, null);
	}

	private void insertRawGoal(UUID userId, String layer, String horizon, String categoryCode,
			LocalDate dueDate) {
		jdbc.update("""
				INSERT INTO goal (id, user_id, content, layer, horizon, category_code, due_date,
								  created_at, updated_at)
				VALUES (?, ?, 'raw', ?, ?, ?, ?, now(), now())
				""", UUID.randomUUID(), userId, layer, horizon, categoryCode, dueDate);
	}

	private UUID persistedUserId() {
		User owner = users.saveAndFlush(new User(Email.of("owner-" + UUID.randomUUID() + "@example.com"),
				"{bcrypt}$2a$10$hash"));
		return owner.getId();
	}

	@Test
	void persistsAndReloadsAGoalWithHorizonAndCategory() {
		UUID userId = persistedUserId();

		goals.saveAndFlush(new Goal(userId, "Przebiec półmaraton", GoalLayer.GOAL,
				GoalHorizon.THIS_YEAR, null, LifeDomain.HEALTH));

		Goal reloaded = goals.findByUserIdOrderByCreatedAtDesc(userId).getFirst();

		assertThat(reloaded.getId()).isNotNull();
		assertThat(reloaded.getId().version()).isEqualTo(7);
		assertThat(reloaded.getUserId()).isEqualTo(userId);
		assertThat(reloaded.getContent()).isEqualTo("Przebiec półmaraton");
		assertThat(reloaded.getLayer()).isEqualTo(GoalLayer.GOAL);
		assertThat(reloaded.getHorizon()).isEqualTo(GoalHorizon.THIS_YEAR);
		assertThat(reloaded.getCategory()).isEqualTo(LifeDomain.HEALTH);
		assertThat(reloaded.getCompletedAt()).isNull();
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void persistsADreamWithoutHorizonOrCategory() {
		UUID userId = persistedUserId();

		goals.saveAndFlush(new Goal(userId, "Pojechać do Japonii", GoalLayer.DREAM, null, null, null));

		Goal reloaded = goals.findByUserIdOrderByCreatedAtDesc(userId).getFirst();

		assertThat(reloaded.getLayer()).isEqualTo(GoalLayer.DREAM);
		assertThat(reloaded.getHorizon()).isNull();
		assertThat(reloaded.getCategory()).isNull();
	}

	/**
	 * S-07's column. {@code due_date} is a {@code date}, not a {@code timestamptz}: what comes back
	 * must be the day the user picked, with no offset arithmetic between the write and the read.
	 */
	@Test
	void persistsATaskWithAndWithoutADueDate() {
		UUID userId = persistedUserId();

		goals.saveAndFlush(new Goal(userId, "Zapłacić za prąd", GoalLayer.TASK, null,
				LocalDate.of(2026, 9, 1), LifeDomain.HOME));
		goals.saveAndFlush(new Goal(userId, "Kupić chleb", GoalLayer.TASK, null, null, null));

		List<Goal> reloaded = goals.findByUserIdOrderByCreatedAtDesc(userId);

		assertThat(reloaded).extracting(Goal::getContent, Goal::getLayer, Goal::getHorizon,
						Goal::getDueDate, Goal::getCategory)
				.containsExactlyInAnyOrder(
						tuple("Zapłacić za prąd", GoalLayer.TASK, null, LocalDate.of(2026, 9, 1),
								LifeDomain.HOME),
						tuple("Kupić chleb", GoalLayer.TASK, null, null, null));
	}

	@Test
	void rejectsAnInconsistentLayerAndTimeFieldsAtConstructionAndOnUpdate() {
		// No row is ever persisted here — every case dies in the constructor or in update(), so a
		// real user id would be an INSERT bought for nothing.
		UUID userId = UUID.randomUUID();
		LocalDate term = LocalDate.of(2026, 9, 1);

		assertThatThrownBy(() -> new Goal(userId, "Cel bez horyzontu", GoalLayer.GOAL, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Goal(userId, "Marzenie z horyzontem", GoalLayer.DREAM,
				GoalHorizon.THIS_YEAR, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Goal(userId, "Zadanie z horyzontem", GoalLayer.TASK,
				GoalHorizon.THIS_YEAR, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Goal(userId, "Cel z terminem", GoalLayer.GOAL,
				GoalHorizon.THIS_YEAR, term, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Goal(userId, "Marzenie z terminem", GoalLayer.DREAM, null, term, null))
				.isInstanceOf(IllegalArgumentException.class);

		// The conversion path re-checks: flipping DREAM -> GOAL without supplying a horizon must fail
		// just as hard as constructing one that way, and so must a task that keeps its term.
		Goal dream = new Goal(userId, "Pojechać do Japonii", GoalLayer.DREAM, null, null, null);
		assertThatThrownBy(() -> dream.update("Pojechać do Japonii", GoalLayer.GOAL, null, null, null))
				.isInstanceOf(IllegalArgumentException.class);

		Goal task = new Goal(userId, "Kupić bilety", GoalLayer.TASK, null, term, null);
		assertThatThrownBy(() -> task.update("Kupić bilety", GoalLayer.GOAL, GoalHorizon.FEW_MONTHS,
				term, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * The entity guard is only the first of three. Writing straight through JDBC bypasses it, which is
	 * exactly how a future writer (a batch import, an AI proposal path) could — so the CHECK constraint
	 * has to hold on its own, over both time fields and all three layers.
	 */
	@Test
	void theDatabaseRejectsInconsistentTimeFieldsEvenWhenTheEntityGuardIsBypassed() {
		UUID userId = persistedUserId();
		LocalDate term = LocalDate.of(2026, 9, 1);

		assertThatThrownBy(() -> insertRawGoal(userId, "GOAL", null, null))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawGoal(userId, "DREAM", "THIS_YEAR", null))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawGoal(userId, "TASK", "THIS_YEAR", null))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawGoal(userId, "GOAL", "THIS_YEAR", null, term))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawGoal(userId, "DREAM", null, null, term))
				.hasMessageContaining("chk_goal_layer_time_fields");
	}

	@Test
	void theDatabaseRejectsACategoryCodeThatIsNotASeededLifeDomain() {
		UUID userId = persistedUserId();

		assertThatThrownBy(() -> insertRawGoal(userId, "DREAM", null, "ASTROLOGY"))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * The stamp is fed sub-microsecond precision on purpose: {@code timestamptz} keeps microseconds,
	 * so a clock finer than that (Linux has one, macOS does not) would let the write response carry
	 * a moment no later read can return.
	 */
	@Test
	void completionIsATimestampThatSurvivesTheRoundTripAndCanBeCleared() {
		UUID userId = persistedUserId();
		OffsetDateTime completedAt = OffsetDateTime.parse("2026-08-17T09:30:00.123456789Z");
		Goal saved = goals.saveAndFlush(new Goal(userId, "Przeczytać 12 książek", GoalLayer.GOAL,
				GoalHorizon.THIS_YEAR, null, LifeDomain.EDUCATION));

		saved.complete(completedAt);
		goals.saveAndFlush(saved);
		assertThat(goals.findByIdAndUserId(saved.getId(), userId).orElseThrow().getCompletedAt())
				.as("what a read returns must equal what the write path already exposed")
				.isEqualTo(saved.getCompletedAt())
				.isEqualTo(OffsetDateTime.parse("2026-08-17T09:30:00.123456Z"));

		saved.reopen();
		goals.saveAndFlush(saved);
		assertThat(goals.findByIdAndUserId(saved.getId(), userId).orElseThrow().getCompletedAt()).isNull();
	}

	/**
	 * S-04b's two columns, and the one piece of logic behind them. Both states live on this row rather
	 * than on the proposal because the <em>user</em> performed them — which is what keeps
	 * {@code ProposalSelector}'s reading of {@code updated_at} as "last interaction" honest. Withdrawal
	 * is idempotent for exactly the reason completion is: a full-replace PUT re-asserts it on every
	 * edit, and a re-stamping {@code withdraw} would move the date each time someone fixed a typo.
	 */
	@Test
	void carriesTheSnoozeAndTheWithdrawalTheUserAskedFor() {
		UUID userId = persistedUserId();
		OffsetDateTime withdrawnAt = OffsetDateTime.parse("2026-08-25T09:30:00.123456789Z");
		Goal saved = goals.saveAndFlush(new Goal(userId, "Wrócić na rower", GoalLayer.GOAL,
				GoalHorizon.THIS_YEAR, null, LifeDomain.HEALTH));

		saved.snoozeUntil(LocalDate.of(2026, 9, 1));
		saved.withdraw(withdrawnAt);
		goals.saveAndFlush(saved);

		Goal reloaded = goals.findByIdAndUserId(saved.getId(), userId).orElseThrow();
		assertThat(reloaded.getRemindAfter()).isEqualTo(LocalDate.of(2026, 9, 1));
		assertThat(reloaded.getWithdrawnAt())
				.isEqualTo(OffsetDateTime.parse("2026-08-25T09:30:00.123456Z"));

		reloaded.withdraw(OffsetDateTime.parse("2026-09-30T12:00:00Z"));
		assertThat(reloaded.getWithdrawnAt())
				.as("withdrawing an already-withdrawn entry keeps the moment the user actually said no")
				.isEqualTo(OffsetDateTime.parse("2026-08-25T09:30:00.123456Z"));

		reloaded.restore();
		goals.saveAndFlush(reloaded);
		assertThat(goals.findByIdAndUserId(saved.getId(), userId).orElseThrow().getWithdrawnAt())
				.as("a withdrawal is reversible — that is the whole reason it is a timestamp and not a delete")
				.isNull();
	}

	@Test
	void scopesReadsToTheOwner() {
		UUID alice = persistedUserId();
		UUID bob = persistedUserId();
		goals.saveAndFlush(new Goal(alice, "Cel Alicji", GoalLayer.DREAM, null, null, null));

		assertThat(goals.findByUserIdOrderByCreatedAtDesc(bob)).isEmpty();
		assertThat(goals.findByIdAndUserId(goals.findByUserIdOrderByCreatedAtDesc(alice).getFirst().getId(), bob))
				.isEmpty();
	}
}
