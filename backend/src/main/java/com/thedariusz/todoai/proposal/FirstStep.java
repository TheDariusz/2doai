package com.thedariusz.todoai.proposal;

import java.util.List;
import java.util.Map;

import com.thedariusz.todoai.ai.JsonSchema;

/**
 * FR-014's opening bullets: what the model returns when the user answers {@code STARTING}, and the
 * schema that constrains it. The two live together because they are one contract — a rename on
 * either side breaks the other, and {@code ProposalPromptTest} holds them to each other.
 *
 * <p><b>Public deliberately</b>, unlike the service and the controller beside it: this is the type
 * handed across the {@code LlmClient} port, so {@code SpringAiLlmClient}'s mapper builds it by
 * reflection and {@code ProposalApiTest} — which lives with the other API tests, not here — names it
 * when it stubs the model.
 *
 * <p>The 3–5 bound is stated twice on purpose: {@code minItems}/{@code maxItems} for the provider,
 * and again in the prompt's own words, because a provider is free to treat the count as advisory.
 * <b>Nothing clamps the result afterwards</b> — a silent truncation to five would hide a model that
 * had stopped following the schema, and the count is not load-bearing for anything downstream.
 */
public record FirstStep(List<String> steps) {

	/**
	 * Strict-mode shaped (object, {@code additionalProperties: false}, every property required) —
	 * the rules {@code OpenRouterLiveTest} proved against Sonnet.
	 */
	static final JsonSchema SCHEMA = new JsonSchema("first_step", Map.of(
			"type", "object",
			"additionalProperties", false,
			"properties", Map.of("steps", Map.of(
					"type", "array",
					"items", Map.of("type", "string"),
					"minItems", 3,
					"maxItems", 5)),
			"required", List.of("steps")));

	public FirstStep {
		// A schema-conformant response always carries the array; a degraded one must still be a
		// value the caller can store rather than an NPE two frames later.
		steps = steps == null ? List.of() : List.copyOf(steps);
	}
}
