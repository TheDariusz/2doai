package com.thedariusz.todoai.proposal;

/**
 * How a proposal was closed — the four responses FR-013 gives the user, plus the two the app writes
 * on their behalf. SCREAMING_CASE like {@code GoalLayer}, so the constant names are what the SPA
 * sends and {@code openapi.yaml} publishes.
 *
 * <p>Two of them look alike and are not: {@link #NOT_NOW} is a short reprieve the user asks for
 * without saying how long, {@link #REMIND_LATER} is one they put a number on (7/30/90 days). Both
 * write {@code goal.remind_after} — one mechanism, different defaults — which is why they are two
 * answers rather than one with an optional field.
 *
 * <p><b>The literals are duplicated across the stack</b> — this enum, {@code openapi.yaml}'s
 * {@code ProposalAnswer} anchor, and the union in {@code ProposalCard.tsx} — and nothing reads the
 * anchor and compares it to both (lessons.md, "a contract value duplicated across the stack needs one
 * guard that spans the boundary"). Named here because it is unguarded: renaming a value means
 * changing three files by hand. {@link #SUPERSEDED} and {@link #ENTRY_CLOSED} are the cheapest cases
 * of it — the SPA never renders either, since both close a proposal that is then no longer pending.
 */
public enum ProposalAnswer {

	/** Starting now: FR-014's first step follows, and the entry is quieted for a week. */
	STARTING,

	/** Not now: a short snooze, no date asked for. */
	NOT_NOW,

	/** Remind me in 7/30/90 days: the same snooze, with the user naming the term. */
	REMIND_LATER,

	/** Never: withdrawn, out of the running until the user restores it. */
	NEVER,

	/**
	 * Closed by the app because the next proposal replaced it (S-05, FR-018) — the implicit "not now"
	 * of a proposal the user simply never answered.
	 *
	 * <p><b>Machine-written, never user-submittable</b>: {@code ProposalAnswerRequest} rejects it with
	 * a 422, so nobody can forge a closure the app is supposed to own. It is a real answer value
	 * rather than a bare {@code answered_at} because {@code proposal_answer_is_whole} forbids the
	 * latter — and because a memory that recorded silence as an answer the user gave would be lying
	 * to the next proposal's prompt.
	 */
	SUPERSEDED,

	/**
	 * Closed by the app because the user completed or withdrew the entry while the proposal about it
	 * was still waiting — answered by what they did rather than by a button. Machine-written and
	 * refused from a client exactly as {@link #SUPERSEDED} is. Unlike that one it neither snoozes the
	 * entry (there is nothing left to quiet) nor records a memory episode: the completion is already
	 * on the entry, and the proposal was simply overtaken by it.
	 */
	ENTRY_CLOSED;

	/** The two closures only the app may write; a client sending either is a 422. */
	boolean isMachineWritten() {
		return this == SUPERSEDED || this == ENTRY_CLOSED;
	}
}
