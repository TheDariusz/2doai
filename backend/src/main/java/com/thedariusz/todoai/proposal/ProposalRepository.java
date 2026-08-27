package com.thedariusz.todoai.proposal;

import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Spring Data access to the {@code proposal} aggregate. Extends the bare {@link Repository} marker
 * for the reason {@code GoalRepository} spells out: {@code JpaRepository}'s inherited finders are
 * unscoped, and the next caller reaches for whichever one compiles. These five are the entire
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

	/**
	 * The same row, read under a write lock, for the transaction that is about to answer it. Two
	 * answers in flight then queue instead of racing: the loser reads the winner's
	 * {@code answered_at} and {@link Proposal#answer} refuses it, rather than both passing a check
	 * against their own detached snapshot and the second silently overwriting the first.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Proposal> findWithLockByIdAndUserId(UUID id, UUID userId);

	/**
	 * FR-019 erasure, driven by {@code ProposalDataDeleter}.
	 *
	 * <p><b>A bulk delete rather than the derived one</b>, because of the cascade: a derived
	 * {@code deleteByUserId} loads the rows and queues an entity delete per row, and
	 * {@code GoalDataDeleter} — same account deletion, same transaction, unspecified order — erases
	 * the parent entries, whose {@code ON DELETE CASCADE} takes those same proposal rows out from
	 * under the persistence context. The queued delete then finds zero rows and Hibernate raises a
	 * stale-state failure, so deleting an account fails for exactly the users who have used the
	 * feature. Issued as SQL immediately, this is order-independent: whichever deleter runs first,
	 * the other finds nothing left to do.
	 */
	@Modifying
	@Query("delete from Proposal p where p.userId = ?1")
	void deleteByUserId(UUID userId);
}
