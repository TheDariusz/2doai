package com.thedariusz.todoai.goal;

/**
 * How far out a long-term goal reaches (FR-004). Required for
 * {@link GoalLayer#GOAL} and forbidden for every other layer — an invariant enforced in
 * {@link Goal}, in the request DTOs, and by the {@code chk_goal_layer_time_fields} CHECK constraint.
 *
 * <p>Constant names are the wire and column literals, anchored in {@code openapi.yaml}'s
 * {@code x-extensible-enum} and guarded by {@code GoalApiTest} — see {@link GoalLayer}.
 */
public enum GoalHorizon {
	THIS_YEAR,
	FEW_MONTHS
}
