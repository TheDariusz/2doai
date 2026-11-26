package com.thedariusz.todoai.reminder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.util.Objects;
import java.util.Optional;

import com.thedariusz.todoai.goal.ReminderLead;
import com.thedariusz.todoai.user.UserZone;

/**
 * When a task's reminder goes out, and until when it still may (DEV-63) — a pure function of the
 * term, with no clock and no database, in the {@code ProposalRhythm} mould. Nothing it computes is
 * stored: the scheduler and the fire both derive the window from the term, so they cannot disagree.
 *
 * <ul>
 * <li>A <b>date-only</b> task is reminded at the morning hour of its day. Its due moment is the end of
 * that day.</li>
 * <li>A <b>timed</b> task is reminded at its due moment minus the lead, or at the morning hour for
 * {@link ReminderLead#MORNING_OF} — never later than the due moment itself.</li>
 * </ul>
 *
 * <p>The expiry is the due moment plus {@link ReminderProperties#lateGrace()}: past it the reminder
 * has nothing left to remind of. The grace is also what lets {@link ReminderLead#AT_TIME} fire at all,
 * since a 60-second tick almost never lands on the due moment exactly.
 *
 * <p>Local times are resolved with {@code atZone}, as {@code ProposalRhythm.next} does, so the two
 * days a year the clock jumps are handled; a lead is subtracted on the timeline, from the
 * <em>zoned</em> due moment, so 00:30 minus an hour is 23:30 the day before.
 */
final class ReminderMoment {

	/** When to send, and the first moment it is too late to. */
	record Window(OffsetDateTime moment, OffsetDateTime expiry) {

		Window {
			Objects.requireNonNull(moment, "moment");
			Objects.requireNonNull(expiry, "expiry");
		}
	}

	private ReminderMoment() {
	}

	/**
	 * @param lead ignored for a date-only task; defaults to {@link ReminderLead#ONE_HOUR} for a timed
	 *        one, as the aggregate does
	 * @return the window, or empty when there is no due date and so nothing to remind of
	 */
	static Optional<Window> of(LocalDate dueDate, LocalTime dueTime, ReminderLead lead,
			ReminderProperties props) {
		if (dueDate == null) {
			return Optional.empty();
		}
		ZonedDateTime morning = dueDate.atTime(props.morningHour(), 0).atZone(UserZone.ZONE);
		if (dueTime == null) {
			ZonedDateTime endOfDay = dueDate.plusDays(1).atStartOfDay(UserZone.ZONE);
			return Optional.of(window(morning, endOfDay, props));
		}
		ZonedDateTime dueAt = dueDate.atTime(dueTime).atZone(UserZone.ZONE);
		ZonedDateTime moment = switch (Objects.requireNonNullElse(lead, ReminderLead.ONE_HOUR)) {
			case AT_TIME -> dueAt;
			case FIFTEEN_MINUTES -> dueAt.minusMinutes(15);
			case ONE_HOUR -> dueAt.minusHours(1);
			case MORNING_OF -> morning.isAfter(dueAt) ? dueAt : morning;
		};
		return Optional.of(window(moment, dueAt, props));
	}

	private static Window window(ZonedDateTime moment, ZonedDateTime dueAt, ReminderProperties props) {
		return new Window(moment.toOffsetDateTime(), dueAt.plus(props.lateGrace()).toOffsetDateTime());
	}
}
