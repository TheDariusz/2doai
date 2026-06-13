package com.thedariusz.todoai.category;

/**
 * The 11 fixed life domains (FR-007) — the stable, type-safe code list.
 * <p>
 * Enum constant names are the canonical category {@code code} values: the in-code
 * mirror of the seeded {@code category} table and the source for the future AI
 * {@code json_schema} enum (FR-008). A fail-fast {@link CategorySyncCheck} asserts
 * this enum and the table agree at startup. Declaration order matches the table's
 * {@code display_order} (1..11).
 */
public enum LifeDomain {
	HEALTH,
	FINANCE,
	CAREER,
	EDUCATION,
	RELATIONSHIPS,
	HOME,
	LEISURE,
	ADMIN,
	SAFETY,
	TRANSPORT,
	INNER_GROWTH
}
