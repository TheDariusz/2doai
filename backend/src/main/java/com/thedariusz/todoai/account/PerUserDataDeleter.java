package com.thedariusz.todoai.account;

import java.util.UUID;

/**
 * The seam by which each module erases <em>its own</em> per-user data for FR-019. Every
 * {@code user_id}-bearing table gets one implementation; {@link AccountDeletionService} discovers
 * them all and calls each.
 *
 * <p>The alternative — one deletion service that knows every table — would need editing by every
 * future slice, and the failure mode of forgetting is silent data retention. Here a module's deleter
 * lives next to the aggregate it deletes.
 *
 * <p>Forgetting one is not caught here but in the schema: every such table carries a foreign key to
 * {@code app_user} with no {@code ON DELETE} action, so the unerased rows make the final user delete
 * fail loudly. That is asserted directly against {@code information_schema} by
 * {@code AccountDeletionIntegrationTest}, which is stronger than anything this interface could
 * declare — a method returning a table name proves only that a deleter was registered, never that it
 * deletes.
 */
public interface PerUserDataDeleter {

	/** Erase every record this module owns for the given user. Runs inside the deletion transaction. */
	void deleteAllForUser(UUID userId);
}
