package com.thedariusz.todoai.goal;

import java.time.OffsetDateTime;
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

/**
 * Round-trips the {@code goal} aggregate against a real Postgres (Testcontainers) with the
 * {@code V6} migration applied. One table carries both layers — a long-term GOAL (horizon
 * required) and a someday DREAM (horizon forbidden) — so the mapping proof has to cover the
 * discriminator, the nullable horizon, and the optional {@code category_code} FK.
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
		jdbc.update("""
				INSERT INTO goal (id, user_id, content, layer, horizon, category_code, created_at, updated_at)
				VALUES (?, ?, 'raw', ?, ?, ?, now(), now())
				""", UUID.randomUUID(), userId, layer, horizon, categoryCode);
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
				GoalHorizon.THIS_YEAR, LifeDomain.HEALTH));

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

		goals.saveAndFlush(new Goal(userId, "Pojechać do Japonii", GoalLayer.DREAM, null, null));

		Goal reloaded = goals.findByUserIdOrderByCreatedAtDesc(userId).getFirst();

		assertThat(reloaded.getLayer()).isEqualTo(GoalLayer.DREAM);
		assertThat(reloaded.getHorizon()).isNull();
		assertThat(reloaded.getCategory()).isNull();
	}

	@Test
	void rejectsALayerHorizonMismatchAtConstructionAndOnUpdate() {
		UUID userId = persistedUserId();

		assertThatThrownBy(() -> new Goal(userId, "Cel bez horyzontu", GoalLayer.GOAL, null, null))
				.isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> new Goal(userId, "Marzenie z horyzontem", GoalLayer.DREAM,
				GoalHorizon.THIS_YEAR, null))
				.isInstanceOf(IllegalArgumentException.class);

		// The conversion path re-checks: flipping DREAM -> GOAL without supplying a horizon must fail
		// just as hard as constructing one that way.
		Goal dream = new Goal(userId, "Pojechać do Japonii", GoalLayer.DREAM, null, null);
		assertThatThrownBy(() -> dream.update("Pojechać do Japonii", GoalLayer.GOAL, null, null))
				.isInstanceOf(IllegalArgumentException.class);
	}

	/**
	 * The entity guard is only the first of three. Writing straight through JDBC bypasses it, which is
	 * exactly how a future writer (a batch import, an AI proposal path) could — so the CHECK constraint
	 * has to hold on its own.
	 */
	@Test
	void theDatabaseRejectsALayerHorizonMismatchEvenWhenTheEntityGuardIsBypassed() {
		UUID userId = persistedUserId();

		assertThatThrownBy(() -> insertRawGoal(userId, "GOAL", null, null))
				.hasMessageContaining("chk_goal_layer_horizon");
		assertThatThrownBy(() -> insertRawGoal(userId, "DREAM", "THIS_YEAR", null))
				.hasMessageContaining("chk_goal_layer_horizon");
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
				GoalHorizon.THIS_YEAR, LifeDomain.EDUCATION));

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

	@Test
	void scopesReadsToTheOwner() {
		UUID alice = persistedUserId();
		UUID bob = persistedUserId();
		goals.saveAndFlush(new Goal(alice, "Cel Alicji", GoalLayer.DREAM, null, null));

		assertThat(goals.findByUserIdOrderByCreatedAtDesc(bob)).isEmpty();
		assertThat(goals.findByIdAndUserId(goals.findByUserIdOrderByCreatedAtDesc(alice).getFirst().getId(), bob))
				.isEmpty();
	}
}
