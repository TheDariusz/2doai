package com.thedariusz.todoai.proposal;

import java.time.Duration;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.mail.MailProperties;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRegistered;
import com.thedariusz.todoai.user.UserRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * The loop that makes the app come back on its own (S-05, FR-011) — the one thing in the product that
 * happens without anybody pressing anything. It owns when each account is next returned to, runs
 * {@link ProposalService#proposeScheduled} when that moment arrives, and emails what came back — the
 * only way to reach a user who is, by definition, not looking at the app.
 *
 * <p><b>The schedule lives in memory, and that is the whole design.</b> Neon bills metered compute
 * and only suspends after ~5 minutes without a query, while the Fly machine that hosts this thread is
 * always-on by necessity. So a tick that asked the database "is anyone due?" — however cheaply —
 * would hold the compute awake 24/7 for the roughly one fire per 2-7 days it exists to perform:
 * ~183 CU-h a month to send a handful of emails (see {@code context/foundation/lessons.md}). The map
 * below is what lets the tick answer that question for free. The database is touched exactly three
 * times: once at boot, once when an account registers, and once per actual fire.
 *
 * <p><b>{@code next_proposal_at} is the map's only backup, and it is enough.</b> A restart reloads
 * it rather than redrawing, so the rhythm survives a deploy instead of bunching around one. The map
 * is authoritative while the JVM lives; the column is authoritative across its death. There is
 * exactly one machine ({@code fly.toml} pins it), so there is nothing to elect and nothing to lock.
 *
 * <p><b>It is also the {@code proposalScheduler} liveness indicator.</b> The failure this feature is
 * uniquely exposed to is the scheduler thread dying while the web server keeps answering — an app
 * that has silently stopped doing the only thing it does unprompted, with no error anywhere. Fly
 * probes liveness ({@code fly.toml}), so reporting the tick's own pulse there is what turns that into
 * a restart. It reads a field and nothing else: a probe that touched the database would undo
 * everything above, fifteen seconds at a time.
 */
@Component
class ProposalScheduler implements HealthIndicator {

	private static final Logger log = LoggerFactory.getLogger(ProposalScheduler.class);

	/**
	 * How often the in-memory schedule is compared against the clock. Free by construction — no I/O
	 * happens unless something is actually due — so the only thing it costs is the precision of the
	 * fire, and a proposal a minute late is a proposal on time.
	 */
	private static final long TICK_MILLIS = 60_000;

	/** A few tick periods: long enough that a slow fire is not a restart, short enough to matter. */
	private static final Duration STALLED_AFTER = Duration.ofMinutes(5);

	/** When each account is next returned to. Written by the fire, by boot, and by registration. */
	private final Map<UUID, OffsetDateTime> schedule = new ConcurrentHashMap<>();

	/** Unseeded and shared: the unpredictability is a product feel, not a secret (ProposalRhythm). */
	private final Random random = new Random();

	private final UserRepository users;

	private final ProposalService proposals;

	private final RhythmProperties rhythm;

	private final EmailSender mail;

	private final MailProperties mailbox;

	/**
	 * Deliberately stale until the first tick lands, so a scheduler whose {@code @Scheduled} method
	 * never got wired reports DOWN instead of reporting the health of a loop that is not running.
	 * The tick starts with the context, well before the web server answers anything, so the window in
	 * which this is honestly DOWN closes before Fly's grace period opens.
	 */
	private volatile Instant lastTick = Instant.EPOCH;

	ProposalScheduler(UserRepository users, ProposalService proposals, RhythmProperties rhythm,
			EmailSender mail, MailProperties mailbox) {
		this.users = users;
		this.proposals = proposals;
		this.rhythm = rhythm;
		this.mail = mail;
		this.mailbox = mailbox;
	}

	/**
	 * The one query per boot. Accounts that already carry a moment resume it; accounts that have never
	 * been scheduled — every account that existed before this slice, and any created while the app was
	 * down — get their first draw here.
	 *
	 * <p>On {@code ApplicationReadyEvent} rather than {@code @PostConstruct}: Flyway and the entity
	 * manager are only guaranteed to be up once the context is, and this is the first thing in the app
	 * to read a table nobody asked it to.
	 */
	@EventListener(ApplicationReadyEvent.class)
	void loadSchedule() {
		OffsetDateTime now = OffsetDateTime.now(ProposalRhythm.USER_ZONE);
		// ponytail: one write per never-scheduled account, in a loop. At MVP scale that is a handful
		// of rows once per boot; a batch update is the upgrade if the account list ever grows.
		for (User account : users.findAll()) {
			if (account.getNextProposalAt() == null) {
				reschedule(account, now);
			}
			else {
				schedule.put(account.getId(), account.getNextProposalAt());
			}
		}
		log.info("Natural rhythm loaded: {} account(s) scheduled", schedule.size());
	}

	/** The pulse. Reads the map, and only the map, unless something is due. */
	@Scheduled(fixedDelay = TICK_MILLIS)
	void tick() {
		lastTick = Instant.now();
		fireDue(OffsetDateTime.now(ProposalRhythm.USER_ZONE));
	}

	/**
	 * The tick's body, with its moment supplied — which is what makes the rhythm testable without
	 * waiting for one.
	 */
	void fireDue(OffsetDateTime now) {
		if (!ProposalRhythm.isInsideWindow(now, rhythm)) {
			return;
		}
		schedule.forEach((account, next) -> {
			if (!next.isAfter(now)) {
				fire(account, now);
			}
		});
	}

	/**
	 * A new account enters the rhythm immediately, rather than at the next restart — which on a
	 * one-machine deploy cadence could be weeks, and would make the app's first act of coming back
	 * depend on when we happened to ship.
	 *
	 * <p>Deliberately a plain {@link EventListener}, so the first drawn moment is written inside the
	 * registration transaction and can no more survive a rolled-back signup than the account itself
	 * can. The map entry can outlive such a rollback; the first fire finds no row and prunes it.
	 */
	@EventListener
	void scheduleNewAccount(UserRegistered event) {
		users.findById(event.userId())
				.ifPresent(account -> reschedule(account, OffsetDateTime.now(ProposalRhythm.USER_ZONE)));
	}

	@Override
	public Health health() {
		Duration sinceLastTick = Duration.between(lastTick, Instant.now());
		return sinceLastTick.compareTo(STALLED_AFTER) > 0
				? Health.down().withDetail("secondsSinceLastTick", sinceLastTick.toSeconds()).build()
				: Health.up().withDetail("scheduled", schedule.size()).build();
	}

	/**
	 * One account's turn. The next moment is drawn <b>whatever happened</b> — nothing to propose, a
	 * failure mid-cycle, an email that would not send — because leaving the old moment in place would
	 * make this account due again on the very next tick, turning the one path that must stay quiet
	 * into a query every 60 seconds. That is why the send sits <em>inside</em> the catch rather than
	 * after it.
	 *
	 * <p><b>The proposal is already saved by the time the email is attempted, and that ordering is the
	 * whole failure plan.</b> A message that cannot be delivered costs the user the nudge, not the
	 * proposal: it stays pending and the card is waiting the next time they open the app. Which is
	 * also why there is no retry queue — the app already has a second channel, and it is the reliable
	 * one.
	 */
	private void fire(UUID accountId, OffsetDateTime now) {
		User account = users.findById(accountId).orElse(null);
		if (account == null) {
			// Deleted since boot (FR-019 runs over HTTP and knows nothing about this map). Forgetting
			// it here is also the only way it stops being due forever.
			schedule.remove(accountId);
			log.info("Dropped account {} from the rhythm: the row is gone", accountId);
			return;
		}
		try {
			// The address rides along on the row this method already loaded for the check above — a
			// second query for it would be one more thing keeping Neon awake.
			proposals.proposeScheduled(accountId)
					.ifPresent(proposal -> mail.send(account.getEmail(), ProposalEmail.subject(proposal),
							ProposalEmail.body(proposal, mailbox.baseUrl())));
		}
		catch (RuntimeException ex) {
			log.error("The rhythm could not return to account {}; the cycle moves on", accountId, ex);
		}
		reschedule(account, now);
	}

	/** Draw the next moment, store it on the row, and hold it in memory until it arrives. */
	private void reschedule(User account, OffsetDateTime from) {
		OffsetDateTime next = ProposalRhythm.next(from, rhythm, random);
		account.scheduleNextProposalAt(next);
		users.saveAndFlush(account);
		schedule.put(account.getId(), next);
	}
}
