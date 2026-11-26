package com.thedariusz.todoai.goal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Objects;
import java.util.UUID;

/**
 * A task's term, or whether it is still in the running, has just changed (DEV-63) — published by
 * {@code GoalService} on a create of a dated task, and on an update that changed the date, time or
 * lead or flipped the entry between active and done/withdrawn. Consumed by the {@code reminder}
 * module, which keeps one pending reminder per dated task in memory.
 *
 * <p>Values, not the entity, for the reason {@link GoalLeftTheRunning} gives — and because it is
 * enough: the listener upserts or removes its map entry from these fields alone, with no query.
 *
 * @param dueDate the term's day, or null when the entry has none (any more)
 * @param dueTime its time of day, or null for a date-only task
 * @param lead how far ahead to remind, set exactly when {@code dueTime} is
 * @param active neither completed nor withdrawn
 * @param reminderSent the reminder for this term already went out
 */
public record GoalTermChanged(UUID userId, UUID goalId, LocalDate dueDate, LocalTime dueTime,
		ReminderLead lead, boolean active, boolean reminderSent) {

	public GoalTermChanged {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(goalId, "goalId");
	}

	static GoalTermChanged of(Goal goal) {
		return new GoalTermChanged(goal.getUserId(), goal.getId(), goal.getDueDate(), goal.getDueTime(),
				goal.getReminderLead(), goal.isActive(), goal.getReminderSentAt() != null);
	}
}
