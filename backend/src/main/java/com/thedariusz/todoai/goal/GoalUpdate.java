package com.thedariusz.todoai.goal;

import java.time.LocalDate;

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
 * is why {@code completed} is a plain {@code boolean} rather than a boxed one: a PATCH-shaped partial
 * update would need every field boxed and a way to tell "absent" from "explicitly null"; S-02 has no
 * case that needs it.
 *
 * <p><b>A primitive here means the client must send it.</b> Jackson 3 (Boot 4) enables
 * {@code FAIL_ON_NULL_FOR_PRIMITIVES} by default, so an omitted {@code completed} is a 400 rather
 * than a silent "active" — fail-closed, and the better behaviour, but it makes every new primitive a
 * <em>breaking</em> wire change that has to land in the same commit as its clients. That is why
 * {@code withdrawn} joined this record here in S-04b Phase 4, alongside the SPA's {@code replace()}
 * that sends it, rather than in Phase 1 with {@code goal.withdrawn_at}.
 *
 * <p><b>{@code withdrawn} is what makes FR-013's "never" reversible.</b> Withdrawal is a field of
 * the entry, so the request that already resends the whole entry is what brings it back — restore
 * needs no route, no verb and no second code path of its own.
 */
public record GoalUpdate(

		@NotBlank
		@Size(max = Goal.MAX_CONTENT_LENGTH)
		String content,

		@NotNull
		GoalLayer layer,

		GoalHorizon horizon,

		LocalDate dueDate,

		LifeDomain categoryCode,

		boolean completed,

		boolean withdrawn) {

	/** See {@link GoalCreation#isTimeFieldsConsistentWithLayer()} — conversion re-checks the rule. */
	@AssertTrue(message = Goal.TIME_FIELDS_RULE)
	boolean isTimeFieldsConsistentWithLayer() {
		return Goal.hasConsistentTimeFields(layer, horizon, dueDate);
	}
}
