package com.thedariusz.todoai.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.thedariusz.todoai.account.PerUserDataDeleter;
import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.mail.MailDeliveryException;
import com.thedariusz.todoai.user.AppLanguage;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import com.thedariusz.todoai.user.UserVerified;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Proving that somebody actually reads the address an account was signed up with (DEV-51). It issues
 * codes, checks them, and owns the two numbers that make a six-digit secret safe: how long it
 * lives, and how many guesses it is worth.
 *
 * <p><b>Nothing here logs a code or a full address.</b> The code is a credential for the minutes it
 * lives, and the address is the account itself — the same rule, and the same {@code @domain} shape,
 * as {@code SmtpEmailSender}.
 *
 * <p><b>The SMTP send is deliberately outside any transaction of ours.</b> Each leg of the transport
 * has a 10-second timeout, and a Hikari connection pinned across that is exactly the kind of held
 * compute the metered database must never pay for. So the code is written by its own targeted
 * update, which commits, and only then is the message handed to the provider — a send that fails
 * leaves an unverified row that the next sign-up or resend simply overwrites.
 */
@Service
public class EmailVerificationService implements PerUserDataDeleter {

	/**
	 * How long a code is worth typing. Named here because the message that carries it says so — and
	 * so does the SPA's verify screen, which spells the number out rather than interpolating it; the
	 * guard in {@code VerificationApiTest} is what stops the two from drifting apart, and is why this
	 * is visible outside the package at all.
	 */
	public static final Duration CODE_VALIDITY = Duration.ofMinutes(15);

	/**
	 * How wide the code is. Named because three layers hardcode it — the generator below, the
	 * {@code @Pattern} on {@link EmailVerificationRequest}, and the SPA's input — and widening it
	 * without moving all three would have the validator reject every code the generator draws. Both
	 * numbers under it are derived, so this is the only place the width is decided.
	 */
	public static final int CODE_LENGTH = 6;

	/** Zero-pads the drawn number to {@link #CODE_LENGTH}. */
	private static final String CODE_FORMAT = "%0" + CODE_LENGTH + "d";

	/** Exclusive upper bound of the drawn number — every value the width can hold. */
	private static final int CODE_BOUND = (int) Math.pow(10, CODE_LENGTH);

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final EmailSender mail;

	private final VerificationThrottle throttle;

	private final ApplicationEventPublisher events;

	/**
	 * A hash of nothing, matched against on every refusal so the clock cannot tell one apart from a
	 * wrong code. BCrypt is the only expensive thing {@link #verify} does, and without this it runs
	 * exclusively for addresses that have an account, are unproved, and still hold a live code — which
	 * is a timing oracle for precisely the set the fixed 403 body exists to hide. {@code AbstractUser
	 * DetailsAuthenticationProvider.mitigateAgainstTimingAttack} is the same move on the login path.
	 */
	private final String dummyCodeHash;

	public EmailVerificationService(UserRepository users, PasswordEncoder passwordEncoder, EmailSender mail,
			VerificationThrottle throttle, ApplicationEventPublisher events) {
		this.dummyCodeHash = passwordEncoder.encode(CODE_FORMAT.formatted(0));
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.mail = mail;
		this.throttle = throttle;
		this.events = events;
	}

	/**
	 * Send a first code to an account that has just been created.
	 *
	 * <p>Called <em>after</em> the registration transaction has committed, which is why it takes an
	 * id rather than a {@link User}: the account it writes to is detached by construction, and the
	 * language is the one this request asked for rather than the one a row read before the write
	 * would report.
	 *
	 * <p><b>The throttle is recorded, not consulted.</b> One row per address means a sign-up can no
	 * longer be replayed to flood a mailbox, so refusing this call would only be a way to strand a
	 * committed account with no code — but the send still starts the cooldown "send again" is measured
	 * against, which is why it is written down.
	 *
	 * @throws MailDeliveryException if the provider would not take the message
	 * @throws IllegalStateException if the row would not take the code — impossible on this path, where
	 *         the account was committed a moment ago and nothing can have proved or deleted it yet, and
	 *         a great deal better than a 201 saying a code was sent
	 */
	public void issue(UUID userId, String email, AppLanguage language) {
		Instant sentAt = Instant.now();
		throttle.record(email, sentAt);
		if (!send(userId, email, language, sentAt)) {
			throw new IllegalStateException("Account " + userId + " would not take the code drawn for it");
		}
	}

	/**
	 * "Send it again." Answers the same whatever the address turns out to be — unknown, already
	 * verified, or waiting — so the endpoint cannot be used to find out which; the throttle is
	 * consulted before the lookup for that same reason.
	 */
	public void resendCode(String rawEmail) {
		String email = Email.normalize(rawEmail);
		Instant sentAt = Instant.now();
		admit(email, sentAt);
		users.findByEmail(email)
				.filter(account -> !account.isEmailVerified())
				.ifPresentOrElse(
						account -> {
							if (!send(account.getId(), email, account.getLanguage(), sentAt)) {
								// The row was proved or deleted between the read and the write. Real here, unlike
								// on the register path: this address has been sitting on a screen with a button.
								log.info("Dropped a code for a @{} address: the row is gone or already proved",
										domainOf(email));
							}
						},
						// An enumeration sweep names each address once and passes the throttle every time, so
						// this is the only line it ever writes. Which of the two it was stays unsaid here for
						// the same reason the response does not say it.
						() -> log.info("Asked for a code for a @{} address with no unverified account",
								domainOf(email)));
	}

	/**
	 * Spend a code. On success the account becomes one the app may act on, and the natural rhythm is
	 * told so <b>inside this transaction</b> — a verification that rolls back must take the first
	 * drawn moment with it.
	 *
	 * <p>{@code noRollbackFor} is the load-bearing part of the annotation: a wrong guess answers 403
	 * and still has to <em>count</em>, and a counter that rolls back with the response it belongs to
	 * is not a cap on anything.
	 *
	 * @throws VerificationFailedException for every way this can go wrong — see that class
	 */
	@Transactional(noRollbackFor = VerificationFailedException.class)
	public void verify(String rawEmail, String submittedCode) {
		OffsetDateTime now = OffsetDateTime.now();
		String email = Email.normalize(rawEmail);
		User account = users.findByEmail(email).orElse(null);
		boolean tryable = account != null && account.hasCodeAwaiting(now);

		// Exactly one BCrypt per request, whatever the answer turns out to be — against the real hash
		// when there is one to check, against a hash of nothing when there is not. See dummyCodeHash.
		boolean matches = passwordEncoder.matches(submittedCode,
				tryable ? account.getVerificationCodeHash() : this.dummyCodeHash);

		if (account == null) {
			throw refuse("no such account", email);
		}
		if (!tryable) {
			throw refuse(whyNotTryable(account, now), email);
		}
		if (!matches) {
			users.recordFailedVerificationAttempt(account.getId(), now);
			if (account.getVerificationAttempts() + 1 >= User.MAX_VERIFICATION_ATTEMPTS) {
				log.warn("Account {} has spent every guess its code was worth — somebody is guessing",
						account.getId());
			}
			throw refuse("wrong code", email);
		}
		if (users.markEmailVerified(account.getId(), now) == 0) {
			// A request racing this one got there first, or the account went away. Either way the caller
			// asked for the address to be proved and it is: 204, and no second UserVerified.
			return;
		}
		events.publishEvent(new UserVerified(account.getId()));
		log.info("Account {} proved its address", account.getId());
	}

	/**
	 * The account is erased (FR-019), so the address owes nothing to the throttle any more — otherwise
	 * the next sign-up with it, by anyone, waits out a cooldown earned by somebody who no longer exists.
	 *
	 * <p>Through {@link PerUserDataDeleter} rather than a call from the delete endpoint, because the
	 * throttle's entry is per-user state like the scheduler's map, and that interface is the one place
	 * a reader can see all of it. A second erasure path — the cleanup of dead unverified sign-ups
	 * {@code User.emailVerifiedAt} invites, say — then cannot forget this one.
	 *
	 * <p>The row is still there: {@link com.thedariusz.todoai.account.AccountDeletionService} loads it
	 * before running the deleters and removes it after, so this is a first-level-cache hit rather than a
	 * query. Running inside that transaction means a rollback leaves the throttle short an entry — the
	 * same trade {@code ProposalScheduler.deleteAllForUser} already takes, and harmless for the same
	 * reason: the entry only ever refuses to send, never allows.
	 */
	@Override
	public void deleteAllForUser(UUID userId) {
		users.findById(userId).map(User::getEmail).ifPresent(throttle::forget);
	}

	/**
	 * One refusal, logged with the cause the caller is deliberately not told. The 403 body is fixed for
	 * all six causes — see {@link VerificationFailedException} — and the log is the only place they are
	 * distinguishable at all; without it every failed verification in production is the same line.
	 */
	private VerificationFailedException refuse(String cause, String email) {
		log.info("Refused a verification for a @{} address: {}", domainOf(email), cause);
		return new VerificationFailedException();
	}

	/**
	 * Which part of {@link User#hasCodeAwaiting} said no. A diagnostic mirror of the predicate rather
	 * than a second copy of the rule: the predicate decides, and this only names the answer for the log.
	 */
	private static String whyNotTryable(User account, OffsetDateTime now) {
		if (account.isEmailVerified()) {
			return "already verified";
		}
		if (account.getVerificationCodeHash() == null) {
			return "no code outstanding";
		}
		if (!account.getVerificationExpiresAt().isAfter(now)) {
			return "the code expired";
		}
		return "out of attempts";
	}

	private void admit(String email, Instant now) {
		long wait = throttle.admit(email, now);
		if (wait > 0) {
			log.info("Refused a code to a @{} address for another {}s", domainOf(email), wait);
			throw new VerificationThrottledException(wait);
		}
	}

	/**
	 * Draw a code, write it, mail it.
	 *
	 * @param sentAt the moment the throttle was told about this send, so a refusal by the provider gives
	 *         back that entry and not whichever one happens to be newest
	 * @return whether the row took the code. {@code false} means the account went away between the
	 *         write that created it and this one, or was proved by a request racing it — the update
	 *         cannot re-create it, which is why it is not a save. What that <em>means</em> differs per
	 *         caller, so each one says so itself.
	 */
	private boolean send(UUID userId, String email, AppLanguage language, Instant sentAt) {
		String code = CODE_FORMAT.formatted(RANDOM.nextInt(CODE_BOUND));
		OffsetDateTime now = OffsetDateTime.now();
		if (users.issueVerificationCode(userId, passwordEncoder.encode(code),
				now.plus(CODE_VALIDITY), now) == 0) {
			return false;
		}
		VerificationEmail message = message(language, code);
		try {
			mail.send(email, message.subject(), message.body());
		}
		catch (MailDeliveryException ex) {
			// Nothing was delivered, so nothing is owed: a provider outage must not spend the address's
			// hourly budget and turn the 503's "retry" advice into a 429.
			throttle.refund(email, sentAt);
			throw ex;
		}
		log.info("Issued a verification code to a @{} address", domainOf(email));
		return true;
	}

	private static String domainOf(String email) {
		return StringUtils.substringAfterLast(email, '@');
	}

	/**
	 * One switch, not two. The compiler guarantees a switch over {@link AppLanguage} covers every
	 * locale; nothing guarantees that two of them pick the same class, and {@code case PL ->
	 * VerificationEmailEn.body(code)} compiles perfectly well.
	 */
	private static VerificationEmail message(AppLanguage language, String code) {
		return switch (language) {
			case PL -> new VerificationEmail(VerificationEmailPl.subject(code), VerificationEmailPl.body(code));
			case EN -> new VerificationEmail(VerificationEmailEn.subject(code), VerificationEmailEn.body(code));
		};
	}

	/** The two halves of one message, so they cannot be drawn from two different locales. */
	private record VerificationEmail(String subject, String body) {
	}
}
