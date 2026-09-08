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
	 * <p><b>{@code email_verified_at IS NOT NULL} is in the where clause, not in the caller</b>
	 * (DEV-51) — the same move as {@link #issueVerificationCode}, and for a stronger reason. The
	 * rhythm is the app's only unprompted outbound mail, so "only an address somebody has proved" is
	 * an invariant that must not rest on three entry points each remembering it; the fourth one
	 * somebody adds will not know. A caller can forget the check, a {@code where} clause cannot, and
	 * it reports the refusal exactly as it reports a missing row — 0, which the scheduler already
	 * treats as "drop the map entry".
	 *
	 * <p>{@code updated_at} is set here because a bulk update bypasses Hibernate's
	 * {@code @UpdateTimestamp}, and this row did change.
	 *
	 * @param id the account whose rhythm is moving on
	 * @param next the drawn moment, from {@code ProposalRhythm}
	 * @param now the moment the draw happened, for the audit column
	 * @return 1 when the row was there and proved, 0 when it is gone <em>or unverified</em>
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("""
			update User u set u.nextProposalAt = :next, u.updatedAt = :now
			where u.id = :id and u.emailVerifiedAt is not null
			""")
	int scheduleNextProposalAt(UUID id, OffsetDateTime next, OffsetDateTime now);

	/**
	 * Change one account's language (DEV-49, FR-002).
	 *
	 * <p>A targeted update for the same reason as {@link #scheduleNextProposalAt}, minus the detached
	 * -account hazard: {@link User} deliberately has <b>no setters at all</b>, and leaving one here
	 * would leave a {@code save}-shaped resurrection bug one call away on an aggregate whose whole
	 * point is that it cannot be re-inserted by accident.
	 *
	 * <p>{@code updated_at} is set here because a bulk update bypasses Hibernate's
	 * {@code @UpdateTimestamp}, and this row did change.
	 *
	 * @param id the account switching language
	 * @param language the language chosen
	 * @param now the moment of the switch, for the audit column
	 * @return 1 when the row was there, 0 when the account no longer exists
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("update User u set u.preferredLanguage = :language, u.updatedAt = :now where u.id = :id")
	int updateLanguage(UUID id, AppLanguage language, OffsetDateTime now);

	/**
	 * Hand one account a fresh verification code (DEV-51), from registration or from a resend.
	 *
	 * <p>The attempt count is reset here rather than by a second write, because a new code and the
	 * budget of guesses it may be spent on are one thing: leaving the old count would make five wrong
	 * guesses lock the address out for good, and turn "send me another one" into a button that
	 * changes nothing.
	 *
	 * <p>A targeted update for the reason given on {@link #scheduleNextProposalAt} — and one more:
	 * it is issued after the registration transaction has committed, so the account it writes to is
	 * detached by construction.
	 *
	 * <p><b>{@code email_verified_at IS NULL} is in the where clause, not in the caller.</b> A proved
	 * account has no code outstanding and must not be handed one — a caller can forget that check, a
	 * {@code where} clause cannot — so the callers that check it first are a fast path rather than the
	 * guarantee.
	 *
	 * @param id the account the code was drawn for
	 * @param codeHash the code, encoded with the same {@code PasswordEncoder} as a password
	 * @param expiresAt when the code stops being accepted
	 * @param now the moment it was issued, for the audit column
	 * @return 1 when an unverified row was there, 0 when it is gone <em>or already proved</em>
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("""
			update User u set u.verificationCodeHash = :codeHash, u.verificationExpiresAt = :expiresAt,
			u.verificationAttempts = 0, u.updatedAt = :now
			where u.id = :id and u.emailVerifiedAt is null
			""")
	int issueVerificationCode(UUID id, String codeHash, OffsetDateTime expiresAt, OffsetDateTime now);

	/**
	 * Charge one wrong guess against the outstanding code (DEV-51). Incremented in the database
	 * rather than read-then-written, so two guesses racing each other both count.
	 *
	 * <p>The write has to land even though the request it happens on answers a failure — a counter
	 * that rolls back with the response it belongs to is not a cap on anything.
	 *
	 * <p>{@code email_verified_at IS NULL} for the reason every other write here carries it: a proved
	 * account has no outstanding code to guess at, so a guess racing the verification that proved it
	 * must not leave a count behind on an account that is done with codes.
	 *
	 * @param id the account whose code was guessed at
	 * @param now the moment of the guess, for the audit column
	 * @return 1 when an unverified row was there, 0 when it is gone <em>or already proved</em>
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("""
			update User u set u.verificationAttempts = u.verificationAttempts + 1, u.updatedAt = :now
			where u.id = :id and u.emailVerifiedAt is null
			""")
	int recordFailedVerificationAttempt(UUID id, OffsetDateTime now);

	/**
	 * The address is proved (DEV-51): the account becomes one that can log in and that the natural
	 * rhythm may write to. The code is cleared in the same statement, so a code that has been spent
	 * cannot be spent again — and the attempts spent on it go with it, because they count guesses
	 * against an outstanding code and there is none any more.
	 *
	 * <p>{@code email_verified_at IS NULL} makes it the <b>one</b> write that can prove an address:
	 * two requests racing with the same correct code both pass the read above it, and only one can pass
	 * this. The 0 the loser gets is what keeps {@code UserVerified} — and so the rhythm's first drawn
	 * moment — from being published twice for one account.
	 *
	 * @param id the account whose address was proved
	 * @param now the moment it was proved — the state itself, and the audit column
	 * @return 1 when this call is the one that proved it, 0 when the row is gone <em>or already
	 *         proved by a request racing this one</em>
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("""
			update User u set u.emailVerifiedAt = :now, u.verificationCodeHash = null,
			u.verificationExpiresAt = null, u.verificationAttempts = 0, u.updatedAt = :now
			where u.id = :id and u.emailVerifiedAt is null
			""")
	int markEmailVerified(UUID id, OffsetDateTime now);
}
