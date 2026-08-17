package com.thedariusz.todoai.goal;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the {@code goal} aggregate. Every finder is scoped by {@code userId} per the
 * {@link com.thedariusz.todoai.security.UserOwned} contract — including the by-id lookup, so a
 * client-supplied id can never reach a row it does not own (a foreign id and a nonexistent one are
 * deliberately indistinguishable: both yield the same 404).
 *
 * <p>Unparameterized list + client-side grouping is deliberate at single-user scale; S-08 owns the
 * real filter contract.
 */
public interface GoalRepository extends JpaRepository<Goal, UUID> {

	List<Goal> findByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

	/** FR-019 erasure, driven by {@code GoalDataDeleter}. */
	void deleteByUserId(UUID userId);
}
