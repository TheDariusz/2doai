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
	 * (DEV-51). The rhythm is the app's only unprompted outbound mail, so "only an address somebody
	 * has proved" is an invariant that must not rest on three entry points each remembering it; the
	 * fourth one somebody adds will not know. A caller can forget the check, a {@code where} clause cannot, and
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
	 * Hand one account a fresh code (DEV-51), for the given purpose: from registration, from "send me
	 * a code", and, from release 2, from the deletion dialog.
	 *
	 * <p>The attempt count is reset here rather than by a second write, because a new code and the
	 * budget of guesses it may be spent on are one thing: leaving the old count would make five wrong
	 * guesses lock the address out for good, and turn "send me another one" into a button that
	 * changes nothing.
	 *
	 * <p>A targeted update for the reason given on {@link #scheduleNextProposalAt} — and one more:
	 * the registration path issues it after its own transaction has committed, so the account it
	 * writes to is detached by construction.
	 *
	 * <p><b>A proved account is handed one too.</b> Until passwordless sign-in this write carried
	 * {@code email_verified_at IS NULL}, because a code only ever proved an address; now it is the
	 * sign-in credential, and a proved account is exactly the one that signs in with it. The slot is
	 * single, so the new code replaces whichever code — of either purpose — was outstanding.
	 *
	 * @param id the account the code was drawn for
	 * @param codeHash the code, encoded with the same {@code PasswordEncoder} as a password
	 * @param purpose what the code may be redeemed for
	 * @param expiresAt when the code stops being accepted
	 * @param now the moment it was issued, for the audit column
	 * @return 1 when the row was there, 0 when it is gone
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("""
			update User u set u.verificationCodeHash = :codeHash, u.verificationPurpose = :purpose,
			u.verificationExpiresAt = :expiresAt, u.verificationAttempts = 0, u.updatedAt = :now
			where u.id = :id
			""")
	int issueVerificationCode(UUID id, String codeHash, CodePurpose purpose, OffsetDateTime expiresAt,
			OffsetDateTime now);

	/**
	 * Charge one guess against the outstanding code, <b>before</b> it is checked. Incremented in the
	 * database rather than read-then-written, and only while the code still has guesses left, so the
	 * cap holds under a race: twenty guesses fired at once all pass any read a caller made earlier, and
	 * exactly {@link User#MAX_VERIFICATION_ATTEMPTS} of them get a 1 here. A 0 is a refusal — the code
	 * is spent out, or there is none — and the caller must not compare the guess at all.
	 *
	 * <p>Charged up front rather than on a wrong answer because a check that runs first and charges
	 * afterwards lets every racing guess be compared: the cap would bound the count, not the guesses.
	 * A right code is charged too, and {@link #consumeVerificationCode} resets the count with it.
	 *
	 * <p>The write has to land even though the request it happens on answers a failure — a counter
	 * that rolls back with the response it belongs to is not a cap on anything.
	 *
	 * <p>The ceiling is the constant itself, concatenated into the query: it is a compile-time constant,
	 * so the JPQL and the aggregate cannot disagree about it.
	 *
	 * @param id the account whose code is being guessed at
	 * @param now the moment of the guess, for the audit column
	 * @return 1 when the guess may be checked, 0 when the row is gone, holds no code, or its code has
	 *         no guesses left
	 */
	@Modifying(flushAutomatically = true)
	@Transactional
	@Query("""
			update User u set u.verificationAttempts = u.verificationAttempts + 1, u.updatedAt = :now
			where u.id = :id and u.verificationCodeHash is not null
			and u.verificationAttempts < """ + User.MAX_VERIFICATION_ATTEMPTS)
	int chargeVerificationAttempt(UUID id, OffsetDateTime now);

	/**
	 * Spend the code that was just checked: clear it, its expiry and the guesses charged to it, so it
	 * can never be replayed. Keyed on the hash that was checked, not merely on "a code is there": a
	 * code re-issued between the check and this write is a different code and is left alone, and of
	 * two requests racing with the same right code only one gets the 1.
	 *
	 * <p>It does not prove the address. That is {@link #markEmailVerified}, a separate write, because
	 * "this was the first proof" is a fact one update cannot report while also spending the code.
	 *
	 * <p>{@code clearAutomatically}: the caller loaded the account before this, and reads it again
	 * afterwards to build the session — a stale first-level cache would hand it the spent code.
	 *
	 * @param id the account whose code is being spent
	 * @param codeHash the hash the submitted code was checked against
	 * @param now the moment it was spent, for the audit column
	 * @return 1 when this call spent it, 0 when the row is gone or holds a different code, or none
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Transactional
	@Query("""
			update User u set u.verificationCodeHash = null, u.verificationExpiresAt = null,
			u.verificationAttempts = 0, u.updatedAt = :now
			where u.id = :id and u.verificationCodeHash = :codeHash
			""")
	int consumeVerificationCode(UUID id, String codeHash, OffsetDateTime now);

	/**
	 * The address is proved (DEV-51): the account becomes one the natural rhythm may write to.
	 *
	 * <p>{@code email_verified_at IS NULL} makes it the <b>one</b> write that can prove an address, and
	 * the 1 it returns is the only way to learn that this was the first proof: a sign-in racing the
	 * first one reaches this too, and gets a 0. That 0 is what keeps {@code UserVerified} — and so the
	 * {@code user_registered} line and the rhythm's first drawn moment — from being published twice.
	 * (Sign-ins by an account already proved skip the call altogether.)
	 *
	 * @param id the account whose address was proved
	 * @param now the moment it was proved — the state itself, and the audit column
	 * @return 1 when this call is the one that proved it, 0 when the row is gone <em>or already
	 *         proved</em>
	 */
	@Modifying(flushAutomatically = true, clearAutomatically = true)
	@Transactional
	@Query("""
			update User u set u.emailVerifiedAt = :now, u.updatedAt = :now
			where u.id = :id and u.emailVerifiedAt is null
			""")
	int markEmailVerified(UUID id, OffsetDateTime now);
}
