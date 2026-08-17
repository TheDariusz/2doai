package com.thedariusz.todoai.goal;

/**
 * Which non-task layer an entry belongs to (FR-004 long-term goals, FR-005 someday dreams) — the
 * discriminator on the single {@code goal} table.
 *
 * <p>Constant names <em>are</em> the wire and column literals ({@code @Enumerated(STRING)}; the
 * snake_case JSON strategy leaves enum values untouched). The anchor for that contract is the
 * {@code x-extensible-enum} list in {@code openapi.yaml}; {@code GoalApiTest} reads the anchor and
 * compares it to both this enum and the frontend copy, so renaming any one copy goes red.
 */
public enum GoalLayer {
	GOAL,
	DREAM
}
