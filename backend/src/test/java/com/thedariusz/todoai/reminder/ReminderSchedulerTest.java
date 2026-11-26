package com.thedariusz.todoai.reminder;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.goal.GoalDeleted;
import com.thedariusz.todoai.goal.GoalTermChanged;
import com.thedariusz.todoai.goal.PendingReminderRow;
import com.thedariusz.todoai.goal.ReminderLead;
import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.mail.MailDeliveryException;
import com.thedariusz.todoai.user.UserZone;
import org.junit.jupiter.api.Test;

import org.springframework.boot.health.contributor.Status;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The due-date reminder's timer (DEV-63), asserted without a container, in the
 * {@code ProposalSchedulerTest} mould: the pending reminders are a map and the tick takes its moment
 * as an argument.
 *
 * <p><b>The load-bearing assertion is {@link #touchesNothingAtAllWhileNothingIsDue()}</b>, for the
 * reason {@code ProposalSchedulerTest} gives: a tick that queried anything would keep Neon awake
 * around the clock.
 *
 * <p>Terms are a few days ahead of the real today, because arming reads the real clock: the event
 * listeners decide whether a term has already expired, and only {@code fireDue} takes a synthetic
 * moment.
 */
class ReminderSchedulerTest {

	private static final ReminderProperties PROPS = new ReminderProperties(8, Duration.ofMinutes(5));

	private static final LocalDate DAY = LocalDate.now(UserZone.ZONE).plusDays(3);

	private static final LocalTime NOON = LocalTime.of(12, 0);

	/** {@link #NOON} with {@link ReminderLead#ONE_HOUR}. */
	private static final OffsetDateTime ELEVEN = at(11, 0);

	private static final ReminderMail MAIL = new ReminderMail("owner@example.com", "subject", "body");

	private final ReminderService reminders = mock(ReminderService.class);

	private final EmailSender mail = mock(EmailSender.class);

	private final ReminderScheduler scheduler = new ReminderScheduler(reminders, mail, PROPS);

	private final UUID owner = UUID.randomUUID();

	@Test
	void firesAtTheMoment() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		sends(goal);

		scheduler.fireDue(ELEVEN);

		verify(reminders).fire(goal, owner, window(NOON, ReminderLead.ONE_HOUR), ELEVEN);
		verify(mail).send(MAIL.to(), MAIL.subject(), MAIL.text());
	}

	@Test
	void waitsUntilTheMoment() {
		armed(NOON, ReminderLead.ONE_HOUR);

		scheduler.fireDue(ELEVEN.minusMinutes(1));

		verifyNoInteractions(reminders, mail);
	}

	@Test
	void touchesNothingAtAllWhileNothingIsDue() {
		armed(NOON, ReminderLead.ONE_HOUR);
		armed(null, null);

		scheduler.fireDue(ELEVEN.minusDays(1));

		verifyNoInteractions(reminders, mail);
	}

	/** Downtime over the moment is not a reason to stay quiet while the task is still ahead. */
	@Test
	void firesLateWhileTheDueMomentHasNotPassed() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		sends(goal);

		scheduler.fireDue(at(11, 50));

		verify(mail).send(anyString(), anyString(), anyString());
	}

	/** After the due moment the reminder has nothing left to remind of, and the entry goes silently. */
	@Test
	void dropsTheReminderOnceItsDueMomentAndGraceHavePassed() {
		armed(NOON, ReminderLead.ONE_HOUR);

		scheduler.fireDue(at(12, 5));
		scheduler.fireDue(at(11, 30));

		verifyNoInteractions(reminders, mail);
	}

	/**
	 * The moment of an at-the-time reminder <em>is</em> the due moment, and a 60-second tick almost
	 * never lands on it exactly. The grace is what lets the first tick after it still fire.
	 */
	@Test
	void firesAnAtTimeReminderOnTheFirstTickAfterItsMoment() {
		UUID goal = armed(NOON, ReminderLead.AT_TIME);
		sends(goal);

		scheduler.fireDue(at(12, 0).plusSeconds(40));

		verify(mail).send(anyString(), anyString(), anyString());
	}

	@Test
	void firesOnlyOnce() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		sends(goal);

		scheduler.fireDue(ELEVEN);
		scheduler.fireDue(ELEVEN.plusMinutes(1));

		verify(reminders, times(1)).fire(any(), any(), any(), any());
	}

	@Test
	void forgetsATaskThatWasCompletedOrWithdrawn() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);

		scheduler.onTermChanged(term(goal, NOON, ReminderLead.ONE_HOUR, false, false));
		scheduler.fireDue(ELEVEN);

		verifyNoInteractions(reminders, mail);
	}

	@Test
	void forgetsADeletedTask() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);

		scheduler.onDeleted(new GoalDeleted(owner, goal));
		scheduler.fireDue(ELEVEN);

		verifyNoInteractions(reminders, mail);
	}

	@Test
	void forgetsEveryReminderOfADeletedAccount() {
		armed(NOON, ReminderLead.ONE_HOUR);
		armed(null, null);

		scheduler.deleteAllForUser(owner);
		scheduler.fireDue(ELEVEN);
		scheduler.fireDue(at(8, 0));

		verifyNoInteractions(reminders, mail);
	}

	@Test
	void forgetsATaskWhoseDateWasCleared() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);

		scheduler.onTermChanged(new GoalTermChanged(owner, goal, null, null, null, true, false));
		scheduler.fireDue(ELEVEN);

		verifyNoInteractions(reminders, mail);
	}

	@Test
	void rearmsForTheNewMomentWhenTheTermChanges() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		LocalTime later = LocalTime.of(15, 0);
		scheduler.onTermChanged(term(goal, later, ReminderLead.ONE_HOUR, true, false));
		sends(goal);

		scheduler.fireDue(ELEVEN);
		verifyNoInteractions(reminders);

		scheduler.fireDue(at(14, 0));
		verify(reminders).fire(goal, owner, window(later, ReminderLead.ONE_HOUR), at(14, 0));
	}

	/** Reopening a task whose reminder already went out for this term must not send it again. */
	@Test
	void doesNotRefireAReopenedTaskWhoseReminderWasSent() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);

		scheduler.onTermChanged(term(goal, NOON, ReminderLead.ONE_HOUR, true, true));
		scheduler.fireDue(ELEVEN);

		verifyNoInteractions(reminders, mail);
	}

	/** An overdue task brought back into the running is past reminding: it is never even armed. */
	@Test
	void neverArmsAReopenedTaskThatIsWeeksOverdue() {
		UUID goal = UUID.randomUUID();
		LocalDate weeksAgo = LocalDate.now(UserZone.ZONE).minusWeeks(3);

		scheduler.onTermChanged(new GoalTermChanged(owner, goal, weeksAgo, null, null, true, false));
		scheduler.fireDue(weeksAgo.atTime(9, 0).atZone(UserZone.ZONE).toOffsetDateTime());

		verifyNoInteractions(reminders, mail);
	}

	@Test
	void doesNotLetOneFailingFireStarveTheRest() {
		UUID unlucky = armed(NOON, ReminderLead.ONE_HOUR);
		UUID other = armed(NOON, ReminderLead.ONE_HOUR);
		when(reminders.fire(eq(unlucky), any(), any(), any())).thenThrow(new IllegalStateException("boom"));
		sends(other);

		scheduler.fireDue(ELEVEN);

		verify(reminders).fire(eq(unlucky), any(), any(), any());
		verify(reminders).fire(eq(other), any(), any(), any());
		verify(mail).send(anyString(), anyString(), anyString());
	}

	/**
	 * The entry leaves the map before the fire, but only if it is still the entry the fire read: an
	 * edit that lands while the mail is being sent puts a new entry in, and that one must survive.
	 */
	@Test
	void keepsAnEntryRearmedWhileTheFireWasInFlight() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		LocalTime later = LocalTime.of(15, 0);
		when(reminders.fire(eq(goal), any(), eq(window(NOON, ReminderLead.ONE_HOUR)), any()))
				.thenAnswer(invocation -> {
					scheduler.onTermChanged(term(goal, later, ReminderLead.ONE_HOUR, true, false));
					return new FireOutcome.Send(MAIL);
				});
		when(reminders.fire(eq(goal), any(), eq(window(later, ReminderLead.ONE_HOUR)), any()))
				.thenReturn(FireOutcome.DROP);

		scheduler.fireDue(ELEVEN);
		scheduler.fireDue(at(14, 0));

		verify(reminders).fire(goal, owner, window(later, ReminderLead.ONE_HOUR), at(14, 0));
	}

	/**
	 * A failed lock-and-stamp transaction leaves the row unsent, so the reminder is still owed: it is
	 * retried a minute later, not on the very next tick, and only while it is still before expiry.
	 */
	@Test
	void retriesAFailedFireTransactionAMinuteLater() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		when(reminders.fire(any(), any(), any(), any()))
				.thenThrow(new DataAccessResourceFailureException("neon is asleep"));

		scheduler.fireDue(ELEVEN);
		scheduler.fireDue(ELEVEN.plusSeconds(30));
		verify(reminders, times(1)).fire(any(), any(), any(), any());

		sends(goal);
		scheduler.fireDue(ELEVEN.plusMinutes(1));
		verify(reminders, times(2)).fire(any(), any(), any(), any());
		verify(mail).send(anyString(), anyString(), anyString());
	}

	@Test
	void givesUpRetryingOnceTheDueMomentHasPassed() {
		armed(NOON, ReminderLead.AT_TIME);
		when(reminders.fire(any(), any(), any(), any()))
				.thenThrow(new DataAccessResourceFailureException("neon is asleep"));

		scheduler.fireDue(at(12, 4).plusSeconds(30));
		scheduler.fireDue(at(12, 5).plusSeconds(30));

		verify(reminders, times(1)).fire(any(), any(), any(), any());
	}

	/** Once the row is stamped, the send is final: a retry loop would query Neon every tick. */
	@Test
	void doesNotRetryAFailedSend() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		sends(goal);
		doThrow(new MailDeliveryException("530 authentication required", new IllegalStateException()))
				.when(mail).send(anyString(), anyString(), anyString());

		scheduler.fireDue(ELEVEN);
		scheduler.fireDue(ELEVEN.plusMinutes(1));
		scheduler.fireDue(ELEVEN.plusMinutes(2));

		verify(reminders, times(1)).fire(any(), any(), any(), any());
	}

	@Test
	void sendsNothingForATaskTheFireFoundGoneOrDone() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		when(reminders.fire(eq(goal), any(), any(), any())).thenReturn(FireOutcome.DROP);

		scheduler.fireDue(ELEVEN);
		scheduler.fireDue(ELEVEN.plusMinutes(1));

		verify(reminders, times(1)).fire(any(), any(), any(), any());
		verifyNoInteractions(mail);
	}

	/** A stale entry is re-armed with the row's current window instead of being dropped. */
	@Test
	void rearmsAStaleEntryToTheWindowTheRowActuallyHas() {
		UUID goal = armed(NOON, ReminderLead.ONE_HOUR);
		ReminderMoment.Window current = window(LocalTime.of(15, 0), ReminderLead.ONE_HOUR);
		when(reminders.fire(eq(goal), any(), eq(window(NOON, ReminderLead.ONE_HOUR)), any()))
				.thenReturn(new FireOutcome.Rearm(current));
		when(reminders.fire(eq(goal), any(), eq(current), any()))
				.thenReturn(new FireOutcome.Send(MAIL));

		scheduler.fireDue(ELEVEN);
		verifyNoInteractions(mail);

		scheduler.fireDue(at(14, 0));
		verify(mail).send(MAIL.to(), MAIL.subject(), MAIL.text());
	}

	@Test
	void loadsThePendingRemindersAtBoot() {
		UUID goal = UUID.randomUUID();
		when(reminders.pending(any())).thenReturn(
				List.of(new PendingReminderRow(goal, owner, DAY, NOON, ReminderLead.ONE_HOUR)));
		scheduler.loadPending();
		sends(goal);

		scheduler.fireDue(ELEVEN);

		verify(mail).send(anyString(), anyString(), anyString());
	}

	/**
	 * A late-evening term's grace can cross midnight, so the boot query reads from yesterday and the
	 * expiry check drops the rest in memory.
	 */
	@Test
	void readsFromYesterdayAtBootAndSkipsWhatHasAlreadyExpired() {
		UUID expired = UUID.randomUUID();
		LocalDate yesterday = LocalDate.now(UserZone.ZONE).minusDays(1);
		when(reminders.pending(yesterday)).thenReturn(
				List.of(new PendingReminderRow(expired, owner, yesterday, NOON, ReminderLead.ONE_HOUR)));

		scheduler.loadPending();
		scheduler.fireDue(yesterday.atTime(11, 30).atZone(UserZone.ZONE).toOffsetDateTime());

		verify(reminders).pending(yesterday);
		verify(reminders, never()).fire(any(), any(), any(), any());
	}

	/** An entry a listener wrote while the boot query was in flight is newer than the row the query read. */
	@Test
	void neverOverwritesAnEntryAListenerWroteDuringStartup() {
		UUID goal = armed(LocalTime.of(15, 0), ReminderLead.ONE_HOUR);
		when(reminders.pending(any())).thenReturn(
				List.of(new PendingReminderRow(goal, owner, DAY, NOON, ReminderLead.ONE_HOUR)));
		scheduler.loadPending();

		scheduler.fireDue(ELEVEN);

		verify(reminders, never()).fire(any(), any(), any(), any());
	}

	@Test
	void reportsUpWhileTheTickIsStillRunning() {
		scheduler.tick();

		assertThat(scheduler.health().getStatus()).isEqualTo(Status.UP);
	}

	@Test
	void reportsDownOnceTheTickHasStopped() {
		ReflectionTestUtils.setField(scheduler, "lastTick", Instant.now().minus(Duration.ofHours(1)));

		assertThat(scheduler.health().getStatus()).isEqualTo(Status.DOWN);
	}

	@Test
	void reportsDownUntilTheFirstTickHasEvenHappened() {
		assertThat(scheduler.health().getStatus()).isEqualTo(Status.DOWN);
	}

	/** A fresh task of {@link #owner} on {@link #DAY}, armed the way a saved task is. */
	private UUID armed(LocalTime time, ReminderLead lead) {
		UUID goal = UUID.randomUUID();
		scheduler.onTermChanged(term(goal, time, lead, true, false));
		clearInvocations(reminders, mail);
		return goal;
	}

	private GoalTermChanged term(UUID goal, LocalTime time, ReminderLead lead, boolean active,
			boolean sent) {
		return new GoalTermChanged(owner, goal, DAY, time, lead, active, sent);
	}

	/** {@code doReturn}, so it can replace a stub that throws without calling it. */
	private void sends(UUID goal) {
		doReturn(new FireOutcome.Send(MAIL))
				.when(reminders).fire(eq(goal), any(), any(), any());
	}

	private static ReminderMoment.Window window(LocalTime time, ReminderLead lead) {
		return ReminderMoment.of(DAY, time, lead, PROPS).orElseThrow();
	}

	private static OffsetDateTime at(int hour, int minute) {
		return DAY.atTime(hour, minute).atZone(UserZone.ZONE).toOffsetDateTime();
	}
}
