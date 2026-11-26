package com.thedariusz.todoai.goal;

import java.util.Objects;
import java.util.UUID;

/**
 * An entry was deleted by its owner (DEV-63) — published by {@code GoalService} on a successful
 * delete, so the {@code reminder} module can forget a reminder it would otherwise wake Neon for at
 * its moment, only to find no row. Ids only, as {@link GoalLeftTheRunning} carries.
 */
public record GoalDeleted(UUID userId, UUID goalId) {

	public GoalDeleted {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(goalId, "goalId");
	}
}
