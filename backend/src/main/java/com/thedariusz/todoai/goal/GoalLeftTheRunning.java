package com.thedariusz.todoai.goal;

import java.util.Objects;
import java.util.UUID;

/**
 * An active entry has just been completed or withdrawn by the user — published by
 * {@code GoalService} on the transition, inside the transaction that writes it. Consumed by the
 * proposals module, whose pending proposal may be asking the user to come back to exactly this entry:
 * a question about something already done is the app not listening.
 *
 * <p>An event rather than a call so that {@code goal} keeps not knowing {@code proposal} exists, the
 * same direction {@code UserVerified} keeps between {@code auth} and the rhythm. It carries ids alone,
 * for the reason that record gives: a listener handed a JPA entity starts reasoning about the
 * publisher's persistence context.
 */
public record GoalLeftTheRunning(UUID userId, UUID goalId) {

	public GoalLeftTheRunning {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(goalId, "goalId");
	}
}
