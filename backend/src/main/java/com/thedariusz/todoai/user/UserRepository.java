package com.thedariusz.todoai.user;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Data access to the {@link User} aggregate. Backs authentication (login lookup by email
 * via the {@code AppUserDetailsService}).
 *
 * <p>The finder keys on the normalized (lowercased) email, so callers must pass an already-
 * normalized value (the {@code Email} VO or an {@code AppUserDetailsService} that lowercases the
 * supplied username). The {@code app_user.email} UNIQUE index serves the lookup — no extra index.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	/**
	 * Move the natural rhythm on for one account (S-05, FR-011).
	 *
	 * <p><b>A targeted update rather than a {@code save} of a loaded {@link User}, and that is a
	 * correctness rule rather than a style choice.</b> A fire holds its account <em>detached</em>
	 * across the model call — 60 seconds of Sonnet, then up to 30 of SMTP — so an account deleted in
	 * that window (FR-019) would be merged back by a {@code save}: Hibernate finds no row for the id
	 * and falls through to an INSERT, restoring the email and password hash of an account the user
	 * asked to erase. An {@code update} cannot insert. It matches nothing and says so by returning 0,
	 * which is also how the scheduler learns to drop the entry from its map.
	 *
	 * <p>{@code updated_at} is set here because a bulk update bypasses Hibernate's
	 * {@code @UpdateTimestamp}, and this row did change.
	 *
	 * @param id the account whose rhythm is moving on
	 * @param next the drawn moment, from {@code ProposalRhythm}
	 * @param now the moment the draw happened, for the audit column
	 * @return 1 when the row was there, 0 when the account no longer exists
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("update User u set u.nextProposalAt = :next, u.updatedAt = :now where u.id = :id")
	int scheduleNextProposalAt(UUID id, OffsetDateTime next, OffsetDateTime now);
}
