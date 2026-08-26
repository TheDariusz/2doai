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
	 * Ids ascend in creation order, standing in for the time-ordered UUID v7 keys production uses —
	 * which is what makes the tie-break assertion below meaningful rather than accidental. They are
	 * not v7-shaped ({@code new UUID(0, n)} has no timestamp in its high bits); only the ordering
	 * property the tie-break relies on is reproduced.
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
	void putsAMissedTermAheadOfAnyAmountOfSilence() {
		Candidate overdueTask = new Candidate(id(), GoalLayer.TASK, LifeDomain.FINANCE,
				NOW.toLocalDate().minusDays(9), NOW, false, null, false);
		Candidate abandonedDream = entry(GoalLayer.DREAM, LifeDomain.LEISURE, 100);

		assertThat(select(List.of(overdueTask, abandonedDream)))
				.as("a missed term is the one signal that outranks silence — otherwise an overdue "
						+ "task, being freshly edited, silences its own domain and always sorts last")
				.get().extracting(Selection::id).isEqualTo(overdueTask.id());
	}

	@Test
	void stillBalancesDomainsAmongTheEntriesWhoseTermHasPassed() {
		// Idle three days: under a task's patience, so both are eligible on their term alone.
		Candidate lateInABusyDomain = new Candidate(id(), GoalLayer.TASK, LifeDomain.HEALTH,
				NOW.toLocalDate().minusDays(30), NOW.minusDays(3), false, null, false);
		Candidate noiseKeepingHealthAlive = entry(GoalLayer.GOAL, LifeDomain.HEALTH, 0);
		Candidate lateInAQuietDomain = new Candidate(id(), GoalLayer.TASK, LifeDomain.ADMIN,
				NOW.toLocalDate().minusDays(1), NOW.minusDays(3), false, null, false);

		assertThat(select(List.of(lateInABusyDomain, noiseKeepingHealthAlive, lateInAQuietDomain)))
				.as("promoting missed terms must not switch balancing off inside that tier")
				.get().extracting(Selection::id).isEqualTo(lateInAQuietDomain.id());
	}

	@Test
	void neverProposesATaskTheUserFinishedAfterItsTermHadPassed() {
		Candidate done = new Candidate(id(), GoalLayer.TASK, LifeDomain.HEALTH,
				NOW.toLocalDate().minusDays(2), NOW, true, null, false);

		assertThat(select(List.of(done)))
				.as("ticking a late task off must retire it, not pin it to the top of every proposal")
				.isEmpty();
	}

	@Test
	void countsAnEntryTheUserCompletedTodayAsProofTheDomainIsStillAlive() {
		Candidate abandonedDream = entry(GoalLayer.DREAM, LifeDomain.HEALTH, 100);
		Candidate doneToday = new Candidate(id(), GoalLayer.TASK, LifeDomain.HEALTH, null, NOW, true,
				null, false);
		Candidate quietGoal = entry(GoalLayer.GOAL, LifeDomain.FINANCE, 20);

		assertThat(select(List.of(abandonedDream, doneToday, quietGoal)))
				.as("finishing something in HEALTH is the user speaking — completed rows are why the "
						+ "engine is handed the whole list rather than a findNeglected query")
				.get().extracting(Selection::id).isEqualTo(quietGoal.id());
	}

	@Test
	void breaksATieBetweenEquallySilentDomainsOnRawIdleTime() {
		Candidate olderInOneDomain = entry(GoalLayer.GOAL, LifeDomain.CAREER, 60);
		Candidate newerInAnother = entry(GoalLayer.GOAL, LifeDomain.HOME, 20);

		assertThat(select(List.of(newerInAnother, olderInOneDomain)))
				.as("idle time is the second key outright — it settles equally silent domains too, "
						+ "not only entries inside one domain")
				.get().extracting(Selection::id).isEqualTo(olderInOneDomain.id());
	}

	@Test
	void scoresADomainByItsQuietestEntryWhicheverOrderTheRowsArriveIn() {
		Candidate abandoned = entry(GoalLayer.DREAM, LifeDomain.HEALTH, 100);
		Candidate freshInTheSameDomain = entry(GoalLayer.TASK, LifeDomain.HEALTH, 0);
		Candidate quietGoal = entry(GoalLayer.GOAL, LifeDomain.FINANCE, 20);

		assertThat(select(List.of(abandoned, freshInTheSameDomain, quietGoal)).orElseThrow().id())
				.isEqualTo(quietGoal.id());
		assertThat(select(List.of(freshInTheSameDomain, abandoned, quietGoal)).orElseThrow().id())
				.as("silence folds with min, so the domain scores the same whichever row lands first")
				.isEqualTo(quietGoal.id());
	}

	@Test
	void ignoresEntriesTheUserHasAlreadyCompleted() {
		Candidate done = new Candidate(id(), GoalLayer.DREAM, LifeDomain.HEALTH, null,
				NOW.minusDays(400), true, null, false);

		assertThat(select(List.of(done))).isEmpty();
	}

	@Test
	void neverProposesAnEntryTheUserWithdrew() {
		assertThat(select(List.of(withdrawn(GoalLayer.DREAM, LifeDomain.HEALTH, 400))))
				.as("\"nigdy\" is the user speaking — a withdrawn entry is out of the running until "
						+ "they restore it, however long it then sits")
				.isEmpty();
	}

	@Test
	void stillCountsAWithdrawnEntryAsProofItsDomainWasTouched() {
		Candidate withdrawnToday = withdrawn(GoalLayer.TASK, LifeDomain.HEALTH, 0);
		Candidate abandonedDream = entry(GoalLayer.DREAM, LifeDomain.HEALTH, 100);
		Candidate quietGoal = entry(GoalLayer.GOAL, LifeDomain.FINANCE, 20);

		assertThat(select(List.of(withdrawnToday, abandonedDream, quietGoal)))
				.as("withdrawing is the user interacting, so the silence map is deliberately left "
						+ "alone — dropping withdrawn rows from it would hand HEALTH the proposal back "
						+ "the moment its task went away")
				.get().extracting(Selection::id).isEqualTo(quietGoal.id());
	}

	@Test
	void holdsASnoozedEntryBackUntilTheDayItAskedFor() {
		assertThat(select(List.of(snoozed(1)))).as("tomorrow is still too early").isEmpty();
		assertThat(select(List.of(snoozed(0))))
				.as("remind_after is the day the entry comes back, not the day after it")
				.isPresent();
		assertThat(select(List.of(snoozed(-1)))).as("a lapsed snooze holds nothing back").isPresent();
	}

	@Test
	void prefersTheEntryFromTheDomainTheUserHasBeenSilentInLongest() {
		Candidate abandonedDream = entry(GoalLayer.DREAM, LifeDomain.HEALTH, 100);
		Candidate freshTaskInTheSameDomain = entry(GoalLayer.TASK, LifeDomain.HEALTH, 0);
		Candidate quietGoal = entry(GoalLayer.GOAL, LifeDomain.FINANCE, 20);

		assertThat(select(List.of(abandonedDream, freshTaskInTheSameDomain, quietGoal)))
				.as("HEALTH heard from the user today; FINANCE has been silent for twenty days — "
						+ "raw idle time alone would have flooded HEALTH")
				.get().extracting(Selection::id).isEqualTo(quietGoal.id());
	}

	@Test
	void doesNotChokeOnAnUncategorisedEntry() {
		Candidate uncategorised = entry(GoalLayer.DREAM, null, 40);

		assertThat(select(List.of(uncategorised)))
				.as("category_code is nullable, and a null domain key would have thrown under "
						+ "Collectors#groupingBy — an entry without one is still neglected")
				.get().extracting(Selection::id).isEqualTo(uncategorised.id());
	}

	@Test
	void poolsEveryUncategorisedEntryIntoOneSharedBucket() {
		Candidate abandonedUncategorised = entry(GoalLayer.DREAM, null, 100);
		Candidate freshUncategorised = entry(GoalLayer.TASK, null, 0);
		Candidate quietGoal = entry(GoalLayer.GOAL, LifeDomain.FINANCE, 20);

		assertThat(select(List.of(abandonedUncategorised, freshUncategorised, quietGoal)))
				.as("null is one bucket, not eleven — touching any uncategorised entry quiets them "
						+ "all, so FINANCE is the more silent domain here")
				.get().extracting(Selection::id).isEqualTo(quietGoal.id());
	}

	@Test
	void takesTheLongestIdleEntryOnceTheDomainIsSettled() {
		Candidate quietestDomainsOldest = entry(GoalLayer.GOAL, LifeDomain.CAREER, 90);
		Candidate quietestDomainsNewest = entry(GoalLayer.GOAL, LifeDomain.CAREER, 30);

		assertThat(select(List.of(quietestDomainsOldest, quietestDomainsNewest)))
				.as("silence picks the domain, raw idle time picks within it — a comparator whose "
						+ "second key runs the wrong way still answers every cross-domain case right")
				.get().extracting(Selection::id).isEqualTo(quietestDomainsOldest.id());

		assertThat(select(List.of(quietestDomainsOldest, quietestDomainsNewest)).orElseThrow()
				.neglectedDays())
				.as("the entry's own idle time, not its domain's silence — which here would be 30")
				.isEqualTo(90);
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
		return new Candidate(id(), layer, category, null, NOW.minusDays(idleDays), false, null, false);
	}

	/** An entry the user answered NEVER to, idle the given number of days since. */
	private Candidate withdrawn(GoalLayer layer, LifeDomain category, long idleDays) {
		return new Candidate(id(), layer, category, null, NOW.minusDays(idleDays), false, null, true);
	}

	/** A dream neglected far past its patience, quieted until the given number of days from today. */
	private Candidate snoozed(long offsetDays) {
		return new Candidate(id(), GoalLayer.DREAM, LifeDomain.HEALTH, null, NOW.minusDays(100), false,
				NOW.toLocalDate().plusDays(offsetDays), false);
	}

	/** A task edited this very moment, carrying a term the given number of days from today. */
	private Candidate dueToday(long offsetDays) {
		return new Candidate(id(), GoalLayer.TASK, LifeDomain.HEALTH,
				NOW.toLocalDate().plusDays(offsetDays), NOW, false, null, false);
	}

	private UUID id() {
		return new UUID(0, ++this.nextId);
	}
}
