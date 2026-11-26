package com.thedariusz.todoai.reminder;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalLayer;
import com.thedariusz.todoai.goal.GoalRepository;
import com.thedariusz.todoai.goal.PendingReminderRow;
import com.thedariusz.todoai.mail.MailboxProperties;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The reminder's only two database touches (DEV-63): the boot load, and one short transaction per
 * fire. {@code reminder} depends on {@code goal}, never the reverse.
 */
@Service
class ReminderService {

	private final GoalRepository goals;

	private final UserRepository users;

	private final ReminderProperties props;

	private final MailboxProperties mailbox;

	ReminderService(GoalRepository goals, UserRepository users, ReminderProperties props,
			MailboxProperties mailbox) {
		this.goals = goals;
		this.users = users;
		this.props = props;
		this.mailbox = mailbox;
	}

	@Transactional(readOnly = true)
	List<PendingReminderRow> pending(LocalDate fromDay) {
		return goals.findPendingReminders(fromDay);
	}

	/**
	 * Lock the task, re-check it, stamp it, and render its mail — <b>and send nothing</b>. The send
	 * happens after this commits, outside any transaction and without holding a connection, per the
	 * {@code EmailSender} contract. That ordering makes the reminder at-most-once on purpose: a send
	 * that fails after the stamp is lost, because a retry loop would query Neon every tick for as long
	 * as the mail provider is down. The in-app due chip is the second channel.
	 *
	 * <p>The locked entity is only read. The stamp is a bulk update so it does not move
	 * {@code updated_at} — see {@link GoalRepository#markReminderSent}.
	 *
	 * @param expected the window the scheduler's entry was armed with; a row whose term yields a
	 *        different one is stale, and its current window comes back as {@link FireOutcome.Rearm}
	 * @throws RuntimeException on a database failure, with {@code reminder_sent_at} still null, so the
	 *         scheduler may retry
	 */
	@Transactional
	FireOutcome fire(UUID goalId, UUID userId, ReminderMoment.Window expected, OffsetDateTime now) {
		Goal goal = goals.findWithLockByIdAndUserId(goalId, userId)
				.filter(g -> g.getLayer() == GoalLayer.TASK && g.isActive() && g.getReminderSentAt() == null)
				.orElse(null);
		if (goal == null) {
			return FireOutcome.DROP;
		}
		Optional<ReminderMoment.Window> current =
				ReminderMoment.of(goal.getDueDate(), goal.getDueTime(), goal.getReminderLead(), props)
						.filter(window -> window.isOpenAt(now));
		if (current.isEmpty()) {
			return FireOutcome.DROP;
		}
		if (!current.get().equals(expected)) {
			return new FireOutcome.Rearm(current.get());
		}
		User account = users.findById(userId).filter(User::isEmailVerified).orElse(null);
		if (account == null || goals.markReminderSent(goalId, userId, now) == 0) {
			return FireOutcome.DROP;
		}
		return new FireOutcome.Send(new ReminderMail(account.getEmail(),
				ReminderEmail.subject(account.getLanguage(), goal.getContent()),
				ReminderEmail.body(account.getLanguage(), goal.getContent(), goal.getDueDate(),
						goal.getDueTime(), mailbox.baseUrl())));
	}
}
