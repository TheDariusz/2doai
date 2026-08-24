package com.thedariusz.todoai.proposal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalLayer;

/**
 * Picks the one entry worth bringing back to the user (S-04a, FR-012/FR-015) — the deterministic
 * half of the proactive loop. No database, no clock, no LLM: everything it needs arrives as
 * arguments, which is what makes {@code ProposalSelectorTest} able to state the whole product rule
 * without a container. S-04b (DEV-23) puts an LLM in front of the answer; nothing here calls one.
 *
 * <p><b>Two rules, applied in that order.</b>
 *
 * <p><i>Neglect</i> decides who is eligible. An active entry is neglected once it has been idle for
 * its layer's patience — a week for a current task, a fortnight for a long-term goal, a month for a
 * dream (PRD Open Question #6, which named the middle two; a task is the layer the user expects to
 * move fastest, so it gets the shortest fuse). A task that is past its {@code due_date} is neglected
 * outright, however recently it was edited: it is the only layer carrying an explicit term, and a
 * missed one is a stronger signal than any amount of silence.
 *
 * <p><i>Balancing</i> decides who wins, and it is the reason this is not simply "oldest entry
 * first". FR-012 asks that proposals not flood one life domain. Ranking by raw idle time does the
 * opposite: whichever domain the user neglects hardest owns every proposal forever. So a domain is
 * scored by how long the user has been <b>silent in it</b> — the idle time of its most recently
 * touched entry, completed ones included — and the most silent domain goes first; only inside it
 * does raw idle time break the tie. Touch anything in a domain and it goes quiet for a while, which
 * is what rotates the proposals. An entry with no {@code category_code} is not dropped: null is a
 * domain key like any other, so an uncategorised dream competes on equal terms.
 *
 * <p><b>What "last interaction" is measured from</b> is settled in {@link Candidate#of(Goal)}, and
 * it is {@code updated_at}: today the user is the only writer, so the column already means "when the
 * user last engaged with this". Balancing then falls out for free — answering a proposal is a write,
 * so the entry goes quiet and its domain with it, and the next proposal comes from somewhere else.
 * The ceiling: this makes the clock resettable by any future writer. If S-04b ever stamps the row
 * with bookkeeping the user did not perform (marking an entry as shown, recording a snooze), it must
 * either keep that state off the {@code goal} row or introduce a {@code last_interaction_at} column
 * and move this one line onto it. Both are cheap; getting the column wrong today is not.
 */
final class ProposalSelector {

	/** A current task is the layer the user expects to move this week. */
	static final int TASK_IDLE_DAYS = 7;

	/** PRD Open Question #6's starting point for a long-term goal. */
	static final int GOAL_IDLE_DAYS = 14;

	/** A someday dream is allowed to be a someday dream for a month. */
	static final int DREAM_IDLE_DAYS = 30;

	private ProposalSelector() {
	}

	/**
	 * One entry, reduced to exactly what the two rules read. A flat value rather than the aggregate
	 * so the engine stays free of JPA (and of a {@code Goal} whose {@code @UpdateTimestamp} no unit
	 * test can set), and so the mapping below is the single place the "last interaction" decision is
	 * written down.
	 */
	record Candidate(UUID id, GoalLayer layer, LifeDomain category, LocalDate dueDate,
			OffsetDateTime lastInteractionAt, boolean completed) {

		static Candidate of(Goal goal) {
			return new Candidate(goal.getId(), goal.getLayer(), goal.getCategory(), goal.getDueDate(),
					goal.getUpdatedAt(), goal.getCompletedAt() != null);
		}
	}

	/** The chosen entry's id, and the silence that earned it the proposal. */
	record Selection(UUID id, long neglectedDays) {
	}

	/**
	 * @param entries every entry the user owns, completed ones included — the completed rows never
	 *        win, but they are what tells the engine a domain is still being worked in
	 * @return the entry to propose, or empty when nothing has been neglected long enough
	 */
	static Optional<Selection> select(List<Candidate> entries, OffsetDateTime now) {
		LocalDate today = now.toLocalDate();

		// Null key = uncategorised, which HashMap#merge handles and Collectors#groupingBy would not.
		Map<LifeDomain, Long> silence = new HashMap<>();
		entries.forEach(entry -> silence.merge(entry.category(), idleDays(entry, now), Math::min));

		Comparator<Candidate> mostNeglectedFirst = Comparator
				.comparingLong((Candidate entry) -> silence.get(entry.category()))
				.thenComparingLong(entry -> idleDays(entry, now))
				.reversed()
				// A total order, so the same rows in a different sequence cannot yield a different
				// proposal. Ids are UUID v7, so ascending is oldest-entry-first.
				.thenComparing(Candidate::id);

		return entries.stream()
				.filter(entry -> isNeglected(entry, now, today))
				.min(mostNeglectedFirst)
				.map(entry -> new Selection(entry.id(), idleDays(entry, now)));
	}

	private static boolean isNeglected(Candidate entry, OffsetDateTime now, LocalDate today) {
		if (entry.completed()) {
			return false;
		}
		// dueDate is non-null only on a TASK — the aggregate's layer x time-fields invariant.
		return idleDays(entry, now) >= idlePatience(entry.layer())
				|| (entry.dueDate() != null && entry.dueDate().isBefore(today));
	}

	private static long idleDays(Candidate entry, OffsetDateTime now) {
		return ChronoUnit.DAYS.between(entry.lastInteractionAt(), now);
	}

	private static int idlePatience(GoalLayer layer) {
		return switch (layer) {
			case TASK -> TASK_IDLE_DAYS;
			case GOAL -> GOAL_IDLE_DAYS;
			case DREAM -> DREAM_IDLE_DAYS;
		};
	}
}
