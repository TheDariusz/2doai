package com.thedariusz.todoai.proposal;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.repository.Repository;

/**
 * Spring Data access to the {@code proposal} aggregate. Extends the bare {@link Repository} marker
 * for the reason {@code GoalRepository} spells out: {@code JpaRepository}'s inherited finders are
 * unscoped, and the next caller reaches for whichever one compiles. These four are the entire
 * surface, so the {@link com.thedariusz.todoai.security.UserOwned} isolation contract holds by
 * construction.
 */
public interface ProposalRepository extends Repository<Proposal, UUID> {

	Proposal saveAndFlush(Proposal proposal);

	/**
	 * The pending proposal, if the user has one — the FR-018 slot, read the same way
	 * {@code idx_proposal_one_pending} indexes it. The index guarantees at most one row comes back.
	 */
	Optional<Proposal> findByUserIdAndAnsweredAtIsNull(UUID userId);

	Optional<Proposal> findByIdAndUserId(UUID id, UUID userId);

	/** FR-019 erasure, driven by {@code ProposalDataDeleter}. */
	void deleteByUserId(UUID userId);
}
