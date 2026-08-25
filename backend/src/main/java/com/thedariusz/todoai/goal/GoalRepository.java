package com.thedariusz.todoai.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * Spring Data access to the {@code goal} aggregate. Every finder is scoped by {@code userId} per the
 * {@link com.thedariusz.todoai.security.UserOwned} contract — including the by-id lookup, so a
 * client-supplied id can never reach a row it does not own (a foreign id and a nonexistent one are
 * deliberately indistinguishable: both yield the same 404).
 *
 * <p>That sentence is only true because this extends the bare {@link Repository} marker rather than
 * {@code JpaRepository}: the convenient inherited finders ({@code findById}, {@code findAll},
 * {@code getReferenceById}, {@code deleteById}) are <b>unscoped</b>, and the next person to need a goal
 * by id would otherwise reach for the one that compiles and skip the ownership check. The five
 * methods below are the entire surface, so the isolation contract holds by construction instead of by
 * convention.
 *
 * <p>Unparameterized list + client-side grouping is deliberate at single-user scale — S-08 filters
 * by layer and category in the browser rather than here. A parameterized finder waits for a list
 * that outgrows one round-trip.
 */
public interface GoalRepository extends Repository<Goal, UUID> {

	Goal saveAndFlush(Goal goal);

	List<Goal> findByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

	/** Scoped like the finders, so an id the caller does not own deletes nothing and returns 0. */
	long deleteByIdAndUserId(UUID id, UUID userId);

	/** FR-019 erasure, driven by {@code GoalDataDeleter}. */
	void deleteByUserId(UUID userId);
}
