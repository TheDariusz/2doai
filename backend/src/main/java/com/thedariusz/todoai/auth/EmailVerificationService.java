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
	 * Guesses one code is worth. A six-digit secret is 10^6 wide, so the cap is what keeps it from
	 * being enumerable — and it is reset by every fresh code, so a locked address is one "send again"
	 * away from a new budget rather than dead.
	 */
	private static final int MAX_ATTEMPTS = 5;

	/** Exclusive upper bound of the drawn number; the format pads it to the six digits the API takes. */
	private static final int CODE_BOUND = 1_000_000;

	private static final Logger log = LoggerFactory.getLogger(EmailVerificationService.class);

	private static final SecureRandom RANDOM = new SecureRandom();

	private final UserRepository users;

	private final PasswordEncoder passwordEncoder;

	private final EmailSender mail;

	private final VerificationThrottle throttle;

	private final ApplicationEventPublisher events;

	public EmailVerificationService(UserRepository users, PasswordEncoder passwordEncoder, EmailSender mail,
			VerificationThrottle throttle, ApplicationEventPublisher events) {
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.mail = mail;
		this.throttle = throttle;
		this.events = events;
	}

	/**
	 * Send a first code to an account that has just been created or taken over.
	 *
	 * <p>Called <em>after</em> the registration transaction has committed, which is why it takes an
	 * id rather than a {@link User}: the account it writes to is detached by construction, and the
	 * language is the one this request asked for rather than the one a row read before the write
	 * would report.
	 *
	 * @throws VerificationThrottledException if this address has had too many codes already
	 * @throws MailDeliveryException if the provider would not take the message
	 */
	public void issue(UUID userId, String email, AppLanguage language) {
		admit(email);
		send(userId, email, language);
	}

	/**
	 * "Send it again." Answers the same whatever the address turns out to be — unknown, already
	 * verified, or waiting — so the endpoint cannot be used to find out which; the throttle is
	 * consulted before the lookup for that same reason.
	 */
	public void resendCode(String rawEmail) {
		String email = Email.normalize(rawEmail);
		admit(email);
		users.findByEmail(email)
				.filter(account -> !account.isEmailVerified())
				.ifPresent(account -> send(account.getId(), email, account.getLanguage()));
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
		User account = users.findByEmail(Email.normalize(rawEmail))
				.orElseThrow(VerificationFailedException::new);

		if (account.isEmailVerified() || account.getVerificationCodeHash() == null
				|| account.getVerificationExpiresAt().isBefore(now)
				|| account.getVerificationAttempts() >= MAX_ATTEMPTS) {
			throw new VerificationFailedException();
		}
		if (!passwordEncoder.matches(submittedCode, account.getVerificationCodeHash())) {
			users.recordFailedVerificationAttempt(account.getId(), now);
			throw new VerificationFailedException();
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

	private void admit(String email) {
		long wait = throttle.admit(email, Instant.now());
		if (wait > 0) {
			log.info("Refused a code to a @{} address for another {}s", domainOf(email), wait);
			throw new VerificationThrottledException(wait);
		}
	}

	private void send(UUID userId, String email, AppLanguage language) {
		String code = "%06d".formatted(RANDOM.nextInt(CODE_BOUND));
		OffsetDateTime now = OffsetDateTime.now();
		if (users.issueVerificationCode(userId, passwordEncoder.encode(code),
				now.plus(CODE_VALIDITY), now) == 0) {
			// The account went away between the write that created it and this one, or was proved by a
			// request racing it. Either way there is nobody left to send a code to, and the update cannot
			// re-create them — that is why it is not a save.
			log.info("Dropped a code for account {}: the row is gone or already proved", userId);
			return;
		}
		mail.send(email, subject(language, code), body(language, code));
		log.info("Issued a verification code to a @{} address", domainOf(email));
	}

	private static String domainOf(String email) {
		return StringUtils.substringAfterLast(email, '@');
	}

	private static String subject(AppLanguage language, String code) {
		return switch (language) {
			case PL -> VerificationEmailPl.subject(code);
			case EN -> VerificationEmailEn.subject(code);
		};
	}

	private static String body(AppLanguage language, String code) {
		return switch (language) {
			case PL -> VerificationEmailPl.body(code);
			case EN -> VerificationEmailEn.body(code);
		};
	}
}
