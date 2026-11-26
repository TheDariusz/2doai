package com.thedariusz.todoai.goal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.AppLanguage;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.DefaultTransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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

	@Autowired
	TransactionTemplate transactions;

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
				"{bcrypt}$2a$10$hash", AppLanguage.PL));
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
		assertThatThrownBy(() -> dream.update("Pojechać do Japonii", GoalLayer.GOAL, null, null, null,
				null, null))
				.isInstanceOf(IllegalArgumentException.class);

		Goal task = new Goal(userId, "Kupić bilety", GoalLayer.TASK, null, term, null);
		assertThatThrownBy(() -> task.update("Kupić bilety", GoalLayer.GOAL, GoalHorizon.FEW_MONTHS,
				term, null, null, null))
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

	private void insertRawTask(UUID userId, String layer, String horizon, LocalDate dueDate,
			LocalTime dueTime, String reminderLead) {
		jdbc.update("""
				INSERT INTO goal (id, user_id, content, layer, horizon, due_date, due_time, reminder_lead,
								  created_at, updated_at)
				VALUES (?, ?, 'raw', ?, ?, ?, ?, ?, now(), now())
				""", UUID.randomUUID(), userId, layer, horizon, dueDate, dueTime, reminderLead);
	}

	/**
	 * DEV-63's columns: a time of day only beside a date, only on a task, and always paired with a
	 * reminder lead. The CHECK holds that pairing on its own, so a raw writer cannot leave a time
	 * with no lead for the reminder scheduler to interpret.
	 */
	@Test
	void theDatabaseRejectsATimeOrALeadOutsideATimedTask() {
		UUID userId = persistedUserId();
		LocalDate term = LocalDate.of(2026, 9, 1);
		LocalTime noon = LocalTime.NOON;

		assertThatThrownBy(() -> insertRawTask(userId, "TASK", null, null, noon, "ONE_HOUR"))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawTask(userId, "TASK", null, term, noon, null))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawTask(userId, "TASK", null, term, null, "ONE_HOUR"))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawTask(userId, "GOAL", "THIS_YEAR", null, noon, "ONE_HOUR"))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawTask(userId, "DREAM", null, null, null, "ONE_HOUR"))
				.hasMessageContaining("chk_goal_layer_time_fields");
		assertThatThrownBy(() -> insertRawTask(userId, "TASK", null, term, noon, "DAY_BEFORE"))
				.hasMessageContaining("chk_goal_reminder_lead");

		insertRawTask(userId, "TASK", null, term, noon, "MORNING_OF");
	}

	@Test
	void persistsATimedTaskAndDefaultsItsLead() {
		UUID userId = persistedUserId();

		goals.saveAndFlush(new Goal(userId, "Wizyta u lekarza", GoalLayer.TASK, null,
				LocalDate.of(2026, 9, 1), LocalTime.of(12, 0, 42), null, null));

		Goal reloaded = goals.findByUserIdOrderByCreatedAtDesc(userId).getFirst();

		assertThat(reloaded.getDueTime())
				.as("truncated to minutes, so <input type=time> round-trips byte-identical")
				.isEqualTo(LocalTime.NOON);
		assertThat(reloaded.getReminderLead()).isEqualTo(ReminderLead.ONE_HOUR);
		assertThat(reloaded.getReminderSentAt()).isNull();
	}

	@Test
	void rejectsALeadWithoutATimeAndATimeWithoutADate() {
		UUID userId = UUID.randomUUID();
		LocalDate term = LocalDate.of(2026, 9, 1);

		assertThatThrownBy(() -> new Goal(userId, "Bez godziny", GoalLayer.TASK, null, term, null,
				ReminderLead.AT_TIME, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Goal(userId, "Bez daty", GoalLayer.TASK, null, null, LocalTime.NOON,
				null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Goal(userId, "Cel z godziną", GoalLayer.GOAL, GoalHorizon.THIS_YEAR,
				null, LocalTime.NOON, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * The sent marker belongs to one term. A new date, time or lead is a new reminder the user asked
	 * for, so the marker clears; an edit that leaves the term alone (a typo fix) must not re-arm a
	 * reminder that already went out.
	 */
	@Test
	void aTermChangeClearsTheSentMarkerAndAnUnchangedTermKeepsIt() {
		UUID userId = persistedUserId();
		LocalDate term = LocalDate.of(2026, 9, 1);
		UUID id = goals.saveAndFlush(new Goal(userId, "Wizyta", GoalLayer.TASK, null, term,
				LocalTime.NOON, ReminderLead.ONE_HOUR, null)).getId();
		jdbc.update("UPDATE goal SET reminder_sent_at = now() WHERE id = ?", id);

		boolean typoChanged = transactions.execute(status -> {
			Goal goal = goals.findByIdAndUserId(id, userId).orElseThrow();
			return goal.update("Wizyta u lekarza", GoalLayer.TASK, null, term, LocalTime.NOON,
					ReminderLead.ONE_HOUR, null);
		});
		assertThat(typoChanged).isFalse();
		assertThat(goals.findByIdAndUserId(id, userId).orElseThrow().getReminderSentAt()).isNotNull();

		boolean leadChanged = transactions.execute(status -> {
			Goal goal = goals.findByIdAndUserId(id, userId).orElseThrow();
			return goal.update("Wizyta u lekarza", GoalLayer.TASK, null, term, LocalTime.NOON,
					ReminderLead.FIFTEEN_MINUTES, null);
		});
		assertThat(leadChanged).isTrue();
		assertThat(goals.findByIdAndUserId(id, userId).orElseThrow().getReminderSentAt()).isNull();
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

	/**
	 * The locked read is what stops a read-modify-write of the entry (a PUT, an answer, a supersede)
	 * from writing back a copy that another one has already overtaken: the second reader waits for
	 * the first to commit and then sees its row. Probed from a second connection with {@code NOWAIT},
	 * so the test fails fast instead of hanging when the lock is not there.
	 */
	@Test
	void theLockedReadHoldsTheRowUntilCommit() {
		UUID userId = persistedUserId();
		Goal goal = goals.saveAndFlush(new Goal(userId, "Zamknięty wiersz", GoalLayer.TASK, null, null,
				null));

		transactions.executeWithoutResult(status -> {
			assertThat(goals.findWithLockByIdAndUserId(goal.getId(), userId)).isPresent();

			assertThatThrownBy(() -> new TransactionTemplate(transactions.getTransactionManager(),
					requiresNew()).executeWithoutResult(inner -> jdbc.queryForList(
							"SELECT id FROM goal WHERE id = ? FOR UPDATE NOWAIT", goal.getId())))
					.hasMessageContaining("could not obtain lock");
		});
	}

	private static TransactionDefinition requiresNew() {
		DefaultTransactionDefinition definition = new DefaultTransactionDefinition();
		definition.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
		return definition;
	}
}
