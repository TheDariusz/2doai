package com.thedariusz.todoai.goal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.thedariusz.todoai.category.LifeDomain;

/**
 * Update payload for {@code PUT /api/goals/{id}} (the {@code GoalUpdate} schema in
 * {@code openapi.yaml}) — {@link GoalCreation} plus {@code completed}.
 *
 * <p><b>Full-replace semantics</b>, so one operation covers everything S-02 needs: editing the text,
 * re-categorizing, converting a dream into a goal (and back), and completing or un-completing. That
 * is why {@code completed} is a plain {@code boolean} — an omitted field means "active", not "leave
 * it alone". A PATCH-shaped partial update would need every field boxed and a way to tell "absent"
 * from "explicitly null"; S-02 has no case that needs it.
 */
public record GoalUpdate(

		@NotBlank
		@Size(max = Goal.MAX_CONTENT_LENGTH)
		String content,

		@NotNull
		GoalLayer layer,

		GoalHorizon horizon,

		LifeDomain categoryCode,

		boolean completed) {

	/** See {@link GoalCreation#isHorizonConsistentWithLayer()} — conversion re-checks the same rule. */
	@AssertTrue(message = Goal.HORIZON_RULE)
	boolean isHorizonConsistentWithLayer() {
		return Goal.hasConsistentHorizon(layer, horizon);
	}
}
