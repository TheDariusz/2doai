package com.thedariusz.todoai.proposal;

/**
 * The four responses FR-013 gives the user, and the wire contract for them — SCREAMING_CASE like
 * {@code GoalLayer}, so the constant names are what the SPA sends and {@code openapi.yaml} publishes.
 *
 * <p>Two of them look alike and are not: {@link #NOT_NOW} is a short reprieve the user asks for
 * without saying how long, {@link #REMIND_LATER} is one they put a number on (7/30/90 days). Both
 * write {@code goal.remind_after} — one mechanism, different defaults — which is why they are two
 * answers rather than one with an optional field.
 */
public enum ProposalAnswer {

	/** Starting now: FR-014's first step follows, and the entry is quieted for a week. */
	STARTING,

	/** Not now: a short snooze, no date asked for. */
	NOT_NOW,

	/** Remind me in 7/30/90 days: the same snooze, with the user naming the term. */
	REMIND_LATER,

	/** Never: withdrawn, out of the running until the user restores it. */
	NEVER
}
