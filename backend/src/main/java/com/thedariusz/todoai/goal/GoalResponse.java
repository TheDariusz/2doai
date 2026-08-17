package com.thedariusz.todoai.goal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.category.LifeDomain;

/**
 * Representation of a goal or dream on the wire. Serialized snake_case ({@code category_code},
 * {@code completed_at}, {@code created_at}, {@code updated_at}) by the global Jackson strategy;
 * enum values pass through untouched, which is what makes the constant names the wire contract.
 *
 * <p>{@code completed_at} doubles as the completion flag — a timestamp rather than a boolean,
 * because S-03's memory enrichment needs to know <em>when</em>, not just whether.
 */
public record GoalResponse(
		UUID id,
		String content,
		GoalLayer layer,
		GoalHorizon horizon,
		LifeDomain categoryCode,
		OffsetDateTime completedAt,
		OffsetDateTime createdAt,
		OffsetDateTime updatedAt) {

	static GoalResponse from(Goal goal) {
		return new GoalResponse(goal.getId(), goal.getContent(), goal.getLayer(), goal.getHorizon(),
				goal.getCategory(), goal.getCompletedAt(), goal.getCreatedAt(), goal.getUpdatedAt());
	}

	/**
	 * An object at the top level, never a bare array (Zalando #110) — the same wrapper
	 * {@code CategoryCollection} uses, so a later addition (a count, a cursor once S-08 needs one)
	 * lands beside {@code items} without breaking clients.
	 */
	record GoalCollection(List<GoalResponse> items) {
	}
}
