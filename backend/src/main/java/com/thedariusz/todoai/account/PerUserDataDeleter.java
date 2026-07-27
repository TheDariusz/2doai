package com.thedariusz.todoai.account;

import java.util.UUID;

/**
 * The seam by which each module erases <em>its own</em> per-user data for FR-019. Every module
 * owning a {@code user_id}-bearing table contributes one implementation;
 * {@link AccountDeletionService} discovers them all and calls each.
 *
 * <p>The alternative — one deletion service that knows every table — would need editing by every
 * future slice, and the failure mode of forgetting is silent data retention. Here a module's
 * deleter lives next to the aggregate it deletes, and {@link #userScopedTable()} lets a guard test
 * assert the registry actually covers the schema.
 */
public interface PerUserDataDeleter {

	/** Erase every record this module owns for the given user. Runs inside the deletion transaction. */
	void deleteAllForUser(UUID userId);

	/** The {@code user_id}-bearing table this deleter is responsible for — read by the orphan guard. */
	String userScopedTable();
}
