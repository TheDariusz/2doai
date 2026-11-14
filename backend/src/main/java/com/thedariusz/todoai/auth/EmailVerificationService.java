package com.thedariusz.todoai.auth;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.RejectedExecutionException;

import com.thedariusz.todoai.account.PerUserDataDeleter;
import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.mail.MailConfig;
import com.thedariusz.todoai.mail.MailDeliveryException;
import com.thedariusz.todoai.mail.MailboxProperties;
import com.thedariusz.todoai.user.AppLanguage;
import com.thedariusz.todoai.user.CodePurpose;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import com.thedariusz.todoai.user.UserVerified;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The e-mailed code: issuing it, and redeeming it (DEV-51, passwordless sign-in). A code proves that
 * somebody reads the address, and since passwordless sign-in it is also a sign-in credential — the
 * first code an account redeems proves its address, and every one after that only opens a session.
 * This class owns the numbers that make an eight-digit secret safe: how long it lives, and how many
 * guesses it is worth.
 *
 * <p><b>Nothing here logs a code, a link or a full address.</b> The code is a credential for the
 * minutes it lives, and the address is the account itself — the same rule, and the same
 * {@code @domain} shape, as {@code SmtpEmailSender}.
 *
 * <p><b>The SMTP send is deliberately outside any transaction of ours.</b> Each leg of the transport
 * has a 10-second timeout, and a Hikari connection pinned across that is exactly the kind of held
 * compute the metered database must never pay for. So the code is written by its own targeted
 * update, which commits, and only then is the message handed to the provider.
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
	 * How wide the code is. Eight digits because the code is a sign-in credential: at five guesses a
	 * code and five codes an hour, six digits gave a targeted address about a 20% yearly chance of a
	 * correct guess, eight give about 0.2%. Named because several layers hardcode it — the generator
	 * below, the {@code @Pattern}s on {@link EmailVerificationRequest} and {@link LoginRequest}, the
	 * spec and the SPA's input — and widening it without moving all of them would have a validator
	 * reject every code the generator draws. Both numbers under it are derived, so this is the only
	 * place the width is decided.
	 */
	public static final int CODE_LENGTH = 8;

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

	private final AuthMailBudget budget;

	private final MailboxProperties mailbox;

	private final Executor authMail;

	private final ApplicationEventPublisher events;

	/**
	 * A hash of nothing, matched against on every refusal so the clock cannot tell one apart from a
	 * wrong code. BCrypt is the only expensive thing {@link #redeem} does, and without this it runs
	 * exclusively for addresses that have an account and still hold a live code — which is a timing
	 * oracle for precisely the set the fixed refusal exists to hide. {@code AbstractUserDetails
	 * AuthenticationProvider.mitigateAgainstTimingAttack} is the same move on the password path.
	 */
	private final String dummyCodeHash;

	public EmailVerificationService(UserRepository users, PasswordEncoder passwordEncoder, EmailSender mail,
			VerificationThrottle throttle, AuthMailBudget budget, MailboxProperties mailbox,
			@Qualifier(MailConfig.AUTH_MAIL_EXECUTOR) Executor authMail, ApplicationEventPublisher events) {
		this.dummyCodeHash = passwordEncoder.encode(CODE_FORMAT.formatted(0));
		this.users = users;
		this.passwordEncoder = passwordEncoder;
		this.mail = mail;
		this.throttle = throttle;
		this.budget = budget;
		this.mailbox = mailbox;
		this.authMail = authMail;
		this.events = events;
	}

	/**
	 * Send a first code to an account that has just been created. Synchronous in this release: the
	 * sign-up response still reports a provider failure as 503.
	 *
	 * <p>Called <em>after</em> the registration transaction has committed, which is why it takes an
	 * id rather than a {@link User}: the account it writes to is detached by construction, and the
	 * language is the one this request asked for rather than the one a row read before the write
	 * would report.
	 *
	 * <p><b>The throttle is recorded, not consulted.</b> One row per address means a sign-up cannot be
	 * replayed to flood a mailbox, so refusing this call would only be a way to strand a committed
	 * account with no code — but the send still starts the cooldown "send again" is measured against,
	 * which is why it is written down.
	 *
	 * @throws MailDeliveryException if the provider would not take the message
	 * @throws IllegalStateException if the row would not take the code — impossible on this path, where
	 *         the account was committed a moment ago and nothing can have deleted it yet, and a great
	 *         deal better than a 201 saying a code was sent
	 */
	public void issue(UUID userId, String email, AppLanguage language) {
		Instant sentAt = Instant.now();
		throttle.record(email, sentAt);
		try {
			if (!send(userId, email, language)) {
				throw new IllegalStateException("Account " + userId + " would not take the code drawn for it");
			}
		}
		catch (MailDeliveryException ex) {
			// Nothing was delivered, so nothing is owed: a provider outage must not spend the address's
			// hourly budget and turn the 503's "retry" advice into a 429.
			throttle.refund(email, sentAt);
			throw ex;
		}
	}

	/**
	 * "Send me a code" — for signing in, or for an unproved account whose first code went missing.
	 * Answers the same whatever the address turns out to be, so the endpoint cannot be used to find
	 * out which addresses have accounts.
	 *
	 * <p>The throttle is consulted <b>before</b> the lookup, and everything after it — lookup, daily
	 * budget, code write, send — runs as one task on the auth-mail executor. The request thread
	 * therefore does identical work for every address, and an SMTP round trip that only known
	 * addresses pay cannot show up in the response time.
	 *
	 * <p><b>The budget is the task's, not the request's.</b> Consulted after the lookup, only an
	 * address with an account spends it, so a flood of made-up addresses cannot spend the day's mail
	 * for everyone. Once it is spent a code is dropped and logged rather than refused: the answer is
	 * still the 202 every address gets, so the budget says nothing about which ones have accounts.
	 *
	 * @throws VerificationThrottledException when this address was sent a code too recently
	 * @throws AuthMailUnavailableException when the auth-mail queue is full
	 */
	public void resendCode(String rawEmail) {
		String email = Email.normalize(rawEmail);
		Instant sentAt = Instant.now();
		admit(email, sentAt);
		try {
			authMail.execute(() -> deliverSignInCode(email, sentAt));
		}
		catch (RejectedExecutionException ex) {
			// Nothing was queued, so nothing was taken from the budget; only the throttle is owed back.
			throttle.refund(email, sentAt);
			log.warn("Refused a code to a @{} address: the auth-mail queue is full", domainOf(email));
			throw new AuthMailUnavailableException();
		}
	}

	/**
	 * Spend a code for {@code purpose} and return the account it belongs to, re-read after the spend.
	 * The first code an account ever redeems also proves its address, and only then is
	 * {@link UserVerified} published — <b>inside this transaction</b>, so a redemption that rolls back
	 * takes the rhythm's first drawn moment with it, and {@code RegistrationAuditLogger}, which listens
	 * after commit, logs {@code user_registered} only for a proof that stuck.
	 *
	 * <p>A guess is charged <b>before</b> it is compared, by a write that applies only while the code
	 * still has guesses left — so parallel guesses never exceed {@link User#MAX_VERIFICATION_ATTEMPTS}
	 * per code, and once they reach it the right code is refused like any other.
	 *
	 * <p>{@code noRollbackFor} is the load-bearing part of the annotation: a wrong guess is refused and
	 * still has to <em>count</em>, and a counter that rolls back with the response it belongs to is not
	 * a cap on anything. Callers translate the exception outside this transaction for the same reason.
	 *
	 * @throws VerificationFailedException for every way this can go wrong — see that class
	 */
	@Transactional(noRollbackFor = VerificationFailedException.class)
	public User redeem(String rawEmail, String submittedCode, CodePurpose purpose) {
		OffsetDateTime now = OffsetDateTime.now();
		String email = Email.normalize(rawEmail);
		User account = users.findByEmail(email).orElse(null);
		boolean tryable = account != null && account.hasCodeAwaiting(now, purpose);
		boolean charged = tryable && users.chargeVerificationAttempt(account.getId(), now) == 1;
		String codeHash = charged ? account.getVerificationCodeHash() : this.dummyCodeHash;

		// Exactly one BCrypt per request, whatever the answer turns out to be — against the real hash
		// when there is one to check, against a hash of nothing when there is not. See dummyCodeHash.
		boolean matches = passwordEncoder.matches(submittedCode, codeHash);

		if (account == null) {
			throw refuse("no such account", email);
		}
		if (!tryable) {
			throw refuse(whyNotTryable(account, now, purpose), email);
		}
		if (!charged) {
			throw refuse("out of attempts to a racing guess", email);
		}
		if (!matches) {
			if (account.getVerificationAttempts() + 1 >= User.MAX_VERIFICATION_ATTEMPTS) {
				log.warn("Account {} has spent every guess its code was worth — somebody is guessing",
						account.getId());
			}
			throw refuse("wrong code", email);
		}
		if (users.consumeVerificationCode(account.getId(), codeHash, now) == 0) {
			throw refuse("spent by a request racing this one", email);
		}
		// An address once proved is never un-proved, so only a first proof is worth a round trip; the
		// conditional update still settles which of two racing first proofs publishes the event.
		if (!account.isEmailVerified() && users.markEmailVerified(account.getId(), now) == 1) {
			events.publishEvent(new UserVerified(account.getId()));
			log.info("Account {} proved its address", account.getId());
		}
		return users.findById(account.getId())
				.orElseThrow(() -> refuse("the account went away", email));
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
	 * The task a code request runs on the auth-mail executor. It answers to nobody — the 202 is already
	 * on its way — so a failure here is a log line, and whatever the request spent is given back so
	 * "send again" works at once.
	 *
	 * <p>A code the budget drops keeps its throttle entry: the request was accepted and answered, and
	 * refunding it would only let the same address queue another request that is dropped too. It is
	 * dropped before the code write, so a code already in the inbox stays valid.
	 */
	private void deliverSignInCode(String email, Instant sentAt) {
		boolean budgetSpent = false;
		try {
			Optional<User> found = users.findByEmail(email);
			if (found.isEmpty()) {
				// An enumeration sweep names each address once and passes the throttle every time, so
				// this is the only line it ever writes — and it spends no budget.
				log.info("Asked for a code for a @{} address with no account", domainOf(email));
				return;
			}
			if (!budget.admit(sentAt)) {
				log.atInfo()
					.addKeyValue("event", "auth_mail_code_dropped")
					.log("Dropped a code for a @{} address: the daily auth-mail budget is spent", domainOf(email));
				return;
			}
			budgetSpent = true;
			User account = found.get();
			if (!send(account.getId(), email, account.getLanguage())) {
				// Deleted between the lookup and the write; the update cannot re-create it.
				log.info("Dropped a code for a @{} address: the row is gone", domainOf(email));
			}
		}
		catch (MailDeliveryException ex) {
			giveBack(email, sentAt, budgetSpent);
			log.warn("Could not mail a code to a @{} address; what it spent was refunded", domainOf(email), ex);
		}
		catch (RuntimeException ex) {
			giveBack(email, sentAt, budgetSpent);
			log.error("Could not issue a code to a @{} address", domainOf(email), ex);
		}
	}

	private void giveBack(String email, Instant sentAt, boolean budgetSpent) {
		throttle.refund(email, sentAt);
		if (budgetSpent) {
			budget.refund(sentAt);
		}
	}

	/**
	 * One refusal, logged with the cause the caller is deliberately not told. The response is fixed
	 * for every cause — see {@link VerificationFailedException} — and the log is the only place they
	 * are distinguishable at all; without it every failed redemption in production is the same line.
	 */
	private VerificationFailedException refuse(String cause, String email) {
		log.info("Refused a code for a @{} address: {}", domainOf(email), cause);
		return new VerificationFailedException();
	}

	/**
	 * Which part of {@link User#hasCodeAwaiting} said no. A diagnostic mirror of the predicate rather
	 * than a second copy of the rule: the predicate decides, and this only names the answer for the log.
	 */
	private static String whyNotTryable(User account, OffsetDateTime now, CodePurpose purpose) {
		if (account.getVerificationCodeHash() == null) {
			return "no code outstanding";
		}
		if (account.getVerificationPurpose() != purpose) {
			return "the code was drawn for " + account.getVerificationPurpose();
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
	 * Draw a sign-in code, write it, mail it with its link.
	 *
	 * @return whether the row took the code. {@code false} means the account went away before the
	 *         write — the update cannot re-create it, which is why it is not a save.
	 * @throws MailDeliveryException if the provider would not take the message; the caller decides
	 *         what that costs, because only it knows what it spent
	 */
	private boolean send(UUID userId, String email, AppLanguage language) {
		String code = CODE_FORMAT.formatted(RANDOM.nextInt(CODE_BOUND));
		OffsetDateTime now = OffsetDateTime.now();
		if (users.issueVerificationCode(userId, passwordEncoder.encode(code), CodePurpose.SIGN_IN,
				now.plus(CODE_VALIDITY), now) == 0) {
			return false;
		}
		VerificationEmail message = message(language, code, SignInLink.of(mailbox.baseUrl(), email, code),
				mailbox.baseUrl());
		mail.send(email, message.subject(), message.body(), message.html());
		log.info("Issued a code to a @{} address", domainOf(email));
		return true;
	}

	private static String domainOf(String email) {
		return StringUtils.substringAfterLast(email, '@');
	}

	/**
	 * One switch, not two. The compiler guarantees a switch over {@link AppLanguage} covers every
	 * locale; nothing guarantees that two of them pick the same class, and {@code case PL ->
	 * VerificationEmailEn.body(code, link)} compiles perfectly well.
	 */
	private static VerificationEmail message(AppLanguage language, String code, String link, String baseUrl) {
		return switch (language) {
			case PL -> new VerificationEmail(VerificationEmailPl.subject(code), VerificationEmailPl.body(code, link),
					VerificationEmailPl.html(code, link, baseUrl));
			case EN -> new VerificationEmail(VerificationEmailEn.subject(code), VerificationEmailEn.body(code, link),
					VerificationEmailEn.html(code, link, baseUrl));
		};
	}

	/** The parts of one message, so they cannot be drawn from two different locales. */
	private record VerificationEmail(String subject, String body, String html) {
	}
}
