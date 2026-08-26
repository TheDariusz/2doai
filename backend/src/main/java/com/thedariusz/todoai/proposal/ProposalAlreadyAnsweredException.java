package com.thedariusz.todoai.proposal;

import java.util.UUID;

/**
 * The proposal has an answer already, and FR-013's four responses are each a one-time act: a second
 * one would overwrite what the first did to the entry (a withdrawal quietly becoming a three-day
 * snooze) and, for {@code STARTING}, pay for a second model call to replace bullets the user has
 * already read.
 *
 * <p>409 rather than a fresh 200 because the caller's view of the world is stale, not malformed —
 * and because a double-clicked answer button must be a no-op, not a silent overwrite. The aggregate
 * refuses it too ({@link Proposal#answer}); this is the check that turns the refusal into a status
 * code before any of the answer's other effects have been applied.
 */
public class ProposalAlreadyAnsweredException extends RuntimeException {

	ProposalAlreadyAnsweredException(UUID id) {
		super("Proposal " + id + " has already been answered");
	}
}
