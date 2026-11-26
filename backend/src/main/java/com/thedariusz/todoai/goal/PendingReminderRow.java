package com.thedariusz.todoai.goal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * One owed due-date reminder as the boot load reads it (DEV-63): the term, and whose it is. A
 * projection rather than a {@link Goal}, so the one cross-user read in {@link GoalRepository} hands
 * out no entity anybody could write back.
 */
public record PendingReminderRow(UUID goalId, UUID userId, LocalDate dueDate, LocalTime dueTime,
		ReminderLead lead) {
}
