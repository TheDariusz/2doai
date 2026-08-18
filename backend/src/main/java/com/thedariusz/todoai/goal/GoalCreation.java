package com.thedariusz.todoai.goal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.thedariusz.todoai.category.LifeDomain;

/**
 * Creation payload for {@code POST /api/goals} (the {@code GoalCreation} schema in
 * {@code openapi.yaml}). {@code category_code} is optional — S-09 adds the AI auto-tag, but a user
 * may always leave an entry uncategorized.
 *
 * <p>Note the deliberate <b>422 / 400 split</b>: everything constrained here parses first and fails
 * validation second, so it surfaces as 422 through {@code ApiExceptionHandler}. An unknown enum
 * literal ({@code "layer": "WISH"}) never gets this far — Jackson rejects the body and Spring
 * renders 400.
 */
public record GoalCreation(

		@NotBlank
		@Size(max = Goal.MAX_CONTENT_LENGTH)
		String content,

		@NotNull
		GoalLayer layer,

		GoalHorizon horizon,

		LifeDomain categoryCode) {

	/**
	 * The cross-field invariant, riding the existing bean-validation machinery so it comes back as a
	 * 422 like any other content failure. The rule itself lives on {@link Goal} — this is the same
	 * check the aggregate and the {@code chk_goal_layer_horizon} constraint make, at the outermost of
	 * the three depths.
	 */
	@AssertTrue(message = Goal.HORIZON_RULE)
	boolean isHorizonConsistentWithLayer() {
		return Goal.hasConsistentHorizon(layer, horizon);
	}
}
