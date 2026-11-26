package com.thedariusz.todoai.reminder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

import com.thedariusz.todoai.account.PerUserDataDeleter;
import com.thedariusz.todoai.goal.GoalDeleted;
import com.thedariusz.todoai.goal.GoalTermChanged;
import com.thedariusz.todoai.goal.PendingReminderRow;
import com.thedariusz.todoai.goal.ReminderLead;
import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.user.UserZone;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.boot.health.contributor.Health;
import org.springframework.boot.health.contributor.HealthIndicator;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * The due-date reminder's timer (DEV-63): one e-mail per dated task, at a moment the <b>user</b> set.
 * The AI owns the moment of a proposal; the user owns the moment of a reminder. So no rhythm window
 * applies here, and nothing this class does touches the pending proposal, the rhythm or AI memory.
 *
 * <p><b>The pending reminders live in memory, for the reason {@code ProposalScheduler}'s schedule
 * does</b>: a tick that asked Neon "is anything due?" would keep its compute awake around the clock
 * (see {@code context/foundation/lessons.md}). The database is touched once at boot, on the goal writes
 * that happen anyway (whose events keep this map current with zero queries), and once per fire.
 *
 * <p><b>Nothing about the window is stored</b> — only {@code goal.reminder_sent_at}. The moment and
 * the expiry are derived from the term by {@link ReminderMoment}, so a restart rebuilds the same
 * map from the boot query, and an unsent reminder that expired while the JVM was down simply is not
 * loaded again.
 *
 * <p>It is also the {@code reminderScheduler} liveness indicator, reading a field and nothing else,
 * exactly as {@code ProposalScheduler} is.
 */
@Component
class ReminderScheduler implements HealthIndicator, PerUserDataDeleter {

	private static final Logger log = LoggerFactory.getLogger(ReminderScheduler.class);

	private static final long TICK_MILLIS = 60_000;

	private static final Duration STALLED_AFTER = Duration.ofMinutes(5);

	/**
	 * How long after a failed fire transaction it is tried again — one tick, not every tick, and only
	 * while before expiry, so a Neon outage costs a handful of attempts, each one a query the outage
	 * was going to fail anyway.
	 */
	private static final Duration RETRY_AFTER = Duration.ofMinutes(1);

	/**
	 * One owed reminder.
	 *
	 * @param notBefore null on a fresh entry; set by a failed fire transaction to space the retry
	 */
	record PendingReminder(UUID userId, ReminderMoment.Window window, OffsetDateTime notBefore) {

		boolean isDue(OffsetDateTime now) {
			OffsetDateTime from = notBefore != null && notBefore.isAfter(window.moment())
					? notBefore
					: window.moment();
			return !from.isAfter(now);
		}

		boolean hasExpired(OffsetDateTime now) {
			return !window.isOpenAt(now);
		}
	}

	private final Map<UUID, PendingReminder> pending = new ConcurrentHashMap<>();

	private final ReminderService reminders;

	private final EmailSender mail;

	private final ReminderProperties props;

	/** Deliberately stale until the first tick, for the reason {@code ProposalScheduler} gives. */
	private volatile Instant lastTick = Instant.EPOCH;

	ReminderScheduler(ReminderService reminders, EmailSender mail, ReminderProperties props) {
		this.reminders = reminders;
		this.mail = mail;
		this.props = props;
	}

	/**
	 * The one query per boot. {@code putIfAbsent}, so an entry a listener wrote while the context was
	 * starting — newer than the row this query read — is never overwritten. If the row was in fact
	 * newer, the fire's stale check re-arms it.
	 */
	@EventListener(ApplicationReadyEvent.class)
	void loadPending() {
		OffsetDateTime now = OffsetDateTime.now(UserZone.ZONE);
		LocalDate yesterday = now.toLocalDate().minusDays(1);
		for (PendingReminderRow row : reminders.pending(yesterday)) {
			window(row.dueDate(), row.dueTime(), row.lead(), now).ifPresent(window ->
					pending.putIfAbsent(row.goalId(), new PendingReminder(row.userId(), window, null)));
		}
		log.info("Due-date reminders loaded: {} pending", pending.size());
	}

	@Scheduled(fixedDelay = TICK_MILLIS)
	void tick() {
		lastTick = Instant.now();
		fireDue(OffsetDateTime.now(UserZone.ZONE));
	}

	/**
	 * The tick's body, with its moment supplied. Fires every entry with
	 * {@code max(moment, notBefore) <= now < expiry} and silently prunes every entry past expiry — so
	 * the two never overlap. No I/O unless something fires.
	 */
	void fireDue(OffsetDateTime now) {
		pending.forEach((goalId, entry) -> {
			if (entry.hasExpired(now)) {
				pending.remove(goalId, entry);
			}
			else if (entry.isDue(now)) {
				fire(goalId, entry, now);
			}
		});
	}

	/**
	 * Arm, re-arm or cancel on a write of the task. After commit, because the map is memory only: a
	 * rolled-back write must change nothing here, neither leaving a stale entry nor removing one the
	 * row still has.
	 */
	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onTermChanged(GoalTermChanged event) {
		Optional<ReminderMoment.Window> window = event.active() && !event.reminderSent()
				? window(event.dueDate(), event.dueTime(), event.lead(), OffsetDateTime.now(UserZone.ZONE))
				: Optional.empty();
		window.ifPresentOrElse(
				armed -> pending.put(event.goalId(), new PendingReminder(event.userId(), armed, null)),
				() -> pending.remove(event.goalId()));
	}

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onDeleted(GoalDeleted event) {
		pending.remove(event.goalId());
	}

	/**
	 * FR-019 erasure. Runs inside the deletion transaction; a rollback leaves the map short entries
	 * the rows still have, which the next boot reloads.
	 */
	@Override
	public void deleteAllForUser(UUID userId) {
		pending.values().removeIf(entry -> entry.userId().equals(userId));
	}

	@Override
	public Health health() {
		Duration sinceLastTick = Duration.between(lastTick, Instant.now());
		return sinceLastTick.compareTo(STALLED_AFTER) > 0
				? Health.down().withDetail("secondsSinceLastTick", sinceLastTick.toSeconds()).build()
				: Health.up().withDetail("pending", pending.size()).build();
	}

	/**
	 * One reminder's turn. The entry leaves the map <b>before</b> the fire, with the conditional
	 * {@code remove(goalId, entry)}: it never re-fires every tick, and a re-arm that lands while the
	 * mail is being sent survives, because its {@code put} replaced the entry this removes.
	 *
	 * <p><b>A failed fire transaction re-arms; a failed send does not.</b> If the transaction throws,
	 * {@code reminder_sent_at} is still null and the reminder is still owed, so the entry goes back with
	 * {@code notBefore} a minute out. Once the row is stamped, a send failure is logged and final.
	 *
	 * <p>Nothing escapes: {@code forEach} stops at the first throw, which would cost every entry after
	 * this one its turn. The fire transaction is caught here, and {@link #send} catches its own failure.
	 * A {@link FireOutcome.Rearm} always carries a different window that is still open, because
	 * {@link ReminderService#fire} drops an expired one.
	 */
	private void fire(UUID goalId, PendingReminder entry, OffsetDateTime now) {
		if (!pending.remove(goalId, entry)) {
			return;
		}
		FireOutcome outcome;
		try {
			outcome = reminders.fire(goalId, entry.userId(), entry.window(), now);
		}
		catch (RuntimeException ex) {
			OffsetDateTime retryAt = now.plus(RETRY_AFTER);
			if (entry.window().isOpenAt(retryAt)) {
				pending.putIfAbsent(goalId, new PendingReminder(entry.userId(), entry.window(), retryAt));
			}
			log.error("Could not fire the reminder for goal {} of account {}; retrying at {} if still owed",
					goalId, entry.userId(), retryAt, ex);
			return;
		}
		switch (outcome) {
			case FireOutcome.Send send -> send(goalId, entry.userId(), send.mail());
			case FireOutcome.Rearm rearm ->
					pending.putIfAbsent(goalId, new PendingReminder(entry.userId(), rearm.window(), null));
			case FireOutcome.Drop _ -> {
			}
		}
	}

	/** After commit, outside any transaction. Logged by id only: the address never reaches a log. */
	private void send(UUID goalId, UUID userId, ReminderMail message) {
		try {
			mail.send(message.to(), message.subject(), message.text());
			log.info("Sent the due-date reminder for goal {} of account {}", goalId, userId);
		}
		catch (RuntimeException ex) {
			log.error("The due-date reminder for goal {} of account {} could not be delivered; it is not "
					+ "retried", goalId, userId, ex);
		}
	}

	/** The term's window, unless there is none or it has already expired. */
	private Optional<ReminderMoment.Window> window(LocalDate dueDate, LocalTime dueTime, ReminderLead lead,
			OffsetDateTime now) {
		return ReminderMoment.of(dueDate, dueTime, lead, props).filter(window -> window.isOpenAt(now));
	}
}
