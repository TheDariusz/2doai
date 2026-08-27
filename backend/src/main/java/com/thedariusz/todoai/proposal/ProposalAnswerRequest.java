package com.thedariusz.todoai.proposal;

import java.util.Set;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotNull;

/**
 * What the user answered, on the wire ({@code POST /api/proposals/{id}/answer}). Two fields, and the
 * second is only ever legal beside one value of the first.
 *
 * <p><b>{@code remindInDays} is boxed, unlike {@code GoalUpdate.completed}</b>, and that is the
 * whole point: Jackson 3 would reject an omitted primitive with a 400, and three of the four answers
 * legitimately omit this one. Boxed, absence is a value the contract can talk about — and the rule
 * below is what stops it from meaning two different things.
 *
 * <p>The rule runs <b>both ways</b>. {@code REMIND_LATER} without one of the offered terms has no
 * defensible default (the user picked a number precisely so the app would not pick one), and a term
 * sent alongside any other answer would be silently dropped — a user who asked for 30 days and
 * quietly got three would have no way to tell. Both are 422 rather than a guess.
 */
record ProposalAnswerRequest(

		@NotNull
		ProposalAnswer answer,

		Integer remindInDays) {

	/** The three terms FR-013 offers; the SPA renders exactly these as buttons. */
	static final Set<Integer> REMIND_PRESETS = Set.of(7, 30, 90);

	@AssertTrue(message = "remind_in_days is required for REMIND_LATER (7, 30 or 90) and forbidden otherwise")
	boolean isRemindInDaysConsistentWithAnswer() {
		// Null-guarded before the lookup: Set.of throws on contains(null) rather than answering false.
		return answer == ProposalAnswer.REMIND_LATER
				? remindInDays != null && REMIND_PRESETS.contains(remindInDays)
				: remindInDays == null;
	}
}
