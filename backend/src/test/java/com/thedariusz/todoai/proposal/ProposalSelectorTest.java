package com.thedariusz.todoai.proposal;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.GoalLayer;
import com.thedariusz.todoai.proposal.ProposalSelector.Candidate;
import com.thedariusz.todoai.proposal.ProposalSelector.Selection;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The selection engine's whole contract (S-04a, FR-012/FR-015), asserted as pure logic: no database,
 * no clock, no LLM. That is the point of splitting S-04 — the half that decides <em>what</em> to
 * propose is deterministic and cheap to test hard, so it can be, before the half that decides how to
 * phrase it ever runs.
 *
 * <p>Day counts are written as literals rather than read back from the class under test: a threshold
 * that moves is a product decision, and it should fail here loudly rather than pass tautologically.
 * The one test that does read the constants is the last one, which holds them against the published
 * contract — there, agreement between two copies <em>is</em> the assertion.
 */
class ProposalSelectorTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-24T20:00:00Z");

	/**
	 * Ids ascend in creation order, exactly as the real UUID v7 keys do — which is what makes the
	 * tie-break assertion below meaningful rather than accidental.
	 */
	private long nextId;

	@Test
	void proposesNothingWhenEveryEntryWasTouchedRecently() {
		assertThat(select(List.of(
				entry(GoalLayer.GOAL, LifeDomain.HEALTH, 3),
				entry(GoalLayer.DREAM, LifeDomain.FINANCE, 10),
				entry(GoalLayer.TASK, LifeDomain.CAREER, 1))))
				.isEmpty();
	}

	@Test
	void treatsALongTermGoalAsNeglectedOnTheFourteenthIdleDay() {
		assertThat(select(List.of(entry(GoalLayer.GOAL, LifeDomain.HEALTH, 13)))).isEmpty();
		assertThat(select(List.of(entry(GoalLayer.GOAL, LifeDomain.HEALTH, 14)))).isPresent();
	}

	@Test
	void givesADreamTwiceThatLongBeforeCallingItNeglected() {
		assertThat(select(List.of(entry(GoalLayer.DREAM, LifeDomain.HEALTH, 29)))).isEmpty();
		assertThat(select(List.of(entry(GoalLayer.DREAM, LifeDomain.HEALTH, 30)))).isPresent();
	}

	@Test
	void treatsACurrentTaskAsNeglectedAfterAWeek() {
		assertThat(select(List.of(entry(GoalLayer.TASK, LifeDomain.HEALTH, 6)))).isEmpty();
		assertThat(select(List.of(entry(GoalLayer.TASK, LifeDomain.HEALTH, 7)))).isPresent();
	}

	@Test
	void treatsAnOverdueTaskAsNeglectedHoweverRecentlyItWasEdited() {
		assertThat(select(List.of(dueToday(0)))).as("a task due today is not late yet").isEmpty();
		assertThat(select(List.of(dueToday(-1)))).as("yesterday's term has passed").isPresent();
	}

	@Test
	void ignoresEntriesTheUserHasAlreadyCompleted() {
		Candidate done = new Candidate(id(), GoalLayer.DREAM, LifeDomain.HEALTH, null,
				NOW.minusDays(400), true);

		assertThat(select(List.of(done))).isEmpty();
	}

	@Test
	void prefersTheEntryFromTheDomainTheUserHasBeenSilentInLongest() {
		Candidate abandonedDream = entry(GoalLayer.DREAM, LifeDomain.HEALTH, 100);
		Candidate freshTaskInTheSameDomain = entry(GoalLayer.TASK, LifeDomain.HEALTH, 0);
		Candidate quietGoal = entry(GoalLayer.GOAL, LifeDomain.FINANCE, 20);

		assertThat(select(List.of(abandonedDream, freshTaskInTheSameDomain, quietGoal)))
				.as("HEALTH heard from the user today; FINANCE has been silent for three weeks — "
						+ "raw idle time alone would have flooded HEALTH")
				.get().extracting(Selection::id).isEqualTo(quietGoal.id());
	}

	@Test
	void letsUncategorisedEntriesCompeteAsADomainOfTheirOwn() {
		Candidate uncategorised = entry(GoalLayer.DREAM, null, 40);

		assertThat(select(List.of(uncategorised)))
				.as("category_code is nullable, and an entry without one is still neglected")
				.get().extracting(Selection::id).isEqualTo(uncategorised.id());
	}

	@Test
	void picksTheSameEntryWhateverOrderTheRowsArriveIn() {
		Candidate older = entry(GoalLayer.GOAL, LifeDomain.HEALTH, 20);
		Candidate newer = entry(GoalLayer.GOAL, LifeDomain.FINANCE, 20);

		assertThat(select(List.of(older, newer)).orElseThrow().id()).isEqualTo(older.id());
		assertThat(select(List.of(newer, older)).orElseThrow().id()).isEqualTo(older.id());
	}

	@Test
	void reportsHowLongTheChosenEntryHadBeenIdle() {
		assertThat(select(List.of(entry(GoalLayer.DREAM, LifeDomain.HEALTH, 47))).orElseThrow()
				.neglectedDays())
				.isEqualTo(47);
	}

	/**
	 * The contract-anchor check for this engine (test-plan §6.5): the thresholds are stated in Java
	 * and again, in words, in {@code openapi.yaml}. A number that moves in one and not the other
	 * publishes a rule the engine does not apply — and the spec is what a reviewer reads.
	 */
	@Test
	void publishesTheThresholdsTheEngineActuallyApplies() throws IOException {
		String spec = Files.readString(Path.of("../context/foundation/openapi.yaml"))
				.replaceAll("\\s+", " ");

		assertThat(spec)
				.as("openapi.yaml is the anchor for every rule the stack states twice")
				.contains("%d days for a TASK, %d for a GOAL, %d for a DREAM".formatted(
						ProposalSelector.TASK_IDLE_DAYS, ProposalSelector.GOAL_IDLE_DAYS,
						ProposalSelector.DREAM_IDLE_DAYS));
	}

	private Optional<Selection> select(List<Candidate> entries) {
		return ProposalSelector.select(entries, NOW);
	}

	private Candidate entry(GoalLayer layer, LifeDomain category, long idleDays) {
		return new Candidate(id(), layer, category, null, NOW.minusDays(idleDays), false);
	}

	/** A task edited this very moment, carrying a term the given number of days from today. */
	private Candidate dueToday(long offsetDays) {
		return new Candidate(id(), GoalLayer.TASK, LifeDomain.HEALTH,
				NOW.toLocalDate().plusDays(offsetDays), NOW, false);
	}

	private UUID id() {
		return new UUID(0, ++this.nextId);
	}
}
