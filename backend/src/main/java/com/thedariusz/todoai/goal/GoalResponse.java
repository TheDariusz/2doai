package com.thedariusz.todoai.goal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.thedariusz.todoai.category.LifeDomain;

/**
 * Representation of a goal or dream on the wire. Serialized snake_case ({@code category_code},
 * {@code completed_at}, {@code created_at}, {@code updated_at}) by the global Jackson strategy;
 * enum values pass through untouched, which is what makes the constant names the wire contract.
 *
 * <p>{@code completed_at} doubles as the completion flag — a timestamp rather than a boolean,
 * because S-03's memory enrichment needs to know <em>when</em>, not just whether. {@code withdrawn_at}
 * is the same shape for the same reason, and it is published (rather than kept server-side) because
 * the SPA's withdrawn filter is what makes a withdrawal reversible; {@code remind_after} rides along so a
 * client can say when a quieted entry comes back. {@code due_time} is pinned to {@code "HH:mm"} so it
 * round-trips through {@code <input type="time">} unchanged; {@code reminder_lead} is the stored lead,
 * the defaulted one included. {@code reminder_sent_at} stays server-side.
 */
public record GoalResponse(
		UUID id,
		String content,
		GoalLayer layer,
		GoalHorizon horizon,
		LocalDate dueDate,
		@JsonFormat(pattern = "HH:mm") LocalTime dueTime,
		ReminderLead reminderLead,
		LifeDomain categoryCode,
		OffsetDateTime completedAt,
		LocalDate remindAfter,
		OffsetDateTime withdrawnAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	/** Public so the proposals resource (S-04a) can return a selected entry in this same shape. */
	public static GoalResponse from(Goal goal) {
		return new GoalResponse(goal.getId(), goal.getContent(), goal.getLayer(), goal.getHorizon(),
				goal.getDueDate(), goal.getDueTime(), goal.getReminderLead(), goal.getCategory(), goal.getCompletedAt(), goal.getRemindAfter(),
				goal.getWithdrawnAt(), goal.getCreatedAt(), goal.getUpdatedAt());
	}

	/**
	 * An object at the top level, never a bare array (Zalando #110) — the same wrapper
	 * {@code CategoryCollection} uses, so a later addition (a count, a cursor once the list outgrows
	 * one round-trip) lands beside {@code items} without breaking clients.
	 */
	record GoalCollection(List<GoalResponse> items) {
	}
}
