package com.thedariusz.todoai.reminder;

import java.time.Duration;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

import com.thedariusz.todoai.goal.ReminderLead;
import com.thedariusz.todoai.user.UserZone;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * When a reminder goes out, and until when it may still go out late (DEV-63) — a pure function of
 * the term, so every rule is one call with no clock and no database.
 */
class ReminderMomentTest {

	private static final ReminderProperties PROPS = new ReminderProperties(8, Duration.ofMinutes(5));

	private static final LocalDate DAY = LocalDate.of(2026, 12, 4);

	private static final LocalTime NOON = LocalTime.of(12, 0);

	@Test
	void hasNoWindowForAnUndatedEntry() {
		assertThat(ReminderMoment.of(null, null, null, PROPS)).isEmpty();
	}

	/** A date-only task is a day, so it is reminded of on that day's morning and expires with it. */
	@Test
	void remindsADateOnlyTaskOnTheMorningOfItsDayAndExpiresAtTheEndOfIt() {
		ReminderMoment.Window window = ReminderMoment.of(DAY, null, null, PROPS).orElseThrow();

		assertThat(window.moment()).isEqualTo(warsaw(DAY, 8, 0));
		assertThat(window.expiry()).isEqualTo(warsaw(DAY.plusDays(1), 0, 0).plusMinutes(5));
	}

	@Test
	void remindsATimedTaskAtItsDueMomentForAtTime() {
		assertThat(momentOf(NOON, ReminderLead.AT_TIME)).isEqualTo(warsaw(DAY, 12, 0));
	}

	@Test
	void remindsATimedTaskFifteenMinutesAhead() {
		assertThat(momentOf(NOON, ReminderLead.FIFTEEN_MINUTES)).isEqualTo(warsaw(DAY, 11, 45));
	}

	@Test
	void remindsATimedTaskAnHourAhead() {
		assertThat(momentOf(NOON, ReminderLead.ONE_HOUR)).isEqualTo(warsaw(DAY, 11, 0));
	}

	@Test
	void remindsATimedTaskOnTheMorningOfItsDay() {
		assertThat(momentOf(NOON, ReminderLead.MORNING_OF)).isEqualTo(warsaw(DAY, 8, 0));
	}

	/** A timed task expires at its own due moment, plus the grace that absorbs a late tick. */
	@Test
	void expiresATimedTaskAtItsDueMomentPlusTheGrace() {
		ReminderMoment.Window window =
				ReminderMoment.of(DAY, NOON, ReminderLead.ONE_HOUR, PROPS).orElseThrow();

		assertThat(window.expiry()).isEqualTo(warsaw(DAY, 12, 5));
	}

	/**
	 * The lead is subtracted from the zoned due moment, never from the time of day — otherwise an
	 * early-morning task would wrap round to 23:30 of its <em>own</em> day, after it was due.
	 */
	@Test
	void subtractsALeadThatCrossesMidnightIntoThePreviousDay() {
		assertThat(momentOf(LocalTime.of(0, 30), ReminderLead.ONE_HOUR))
				.isEqualTo(warsaw(DAY.minusDays(1), 23, 30));
	}

	/** The morning is never later than the thing it is a reminder of. */
	@Test
	void clampsTheMorningToADueMomentEarlierThanIt() {
		assertThat(momentOf(LocalTime.of(7, 0), ReminderLead.MORNING_OF)).isEqualTo(warsaw(DAY, 7, 0));
	}

	/**
	 * The last Sunday of March: 02:00 jumps to 03:00. An hour before 03:30 local is 01:30 local,
	 * because the hour is a real hour on the timeline, not a step back on the wall clock.
	 */
	@Test
	void subtractsARealHourAcrossTheSpringForwardGap() {
		LocalDate springForward = LocalDate.of(2026, 3, 29);

		ReminderMoment.Window window = ReminderMoment
				.of(springForward, LocalTime.of(3, 30), ReminderLead.ONE_HOUR, PROPS).orElseThrow();

		assertThat(window.moment())
				.isEqualTo(OffsetDateTime.of(springForward, LocalTime.of(1, 30), ZoneOffset.ofHours(1)));
	}

	/** The last Sunday of October: 02:30 happens twice, and the earlier one is the one meant. */
	@Test
	void resolvesTheRepeatedHourOfTheFallBackDayToItsFirstOccurrence() {
		LocalDate fallBack = LocalDate.of(2026, 10, 25);

		ReminderMoment.Window window = ReminderMoment
				.of(fallBack, LocalTime.of(2, 30), ReminderLead.AT_TIME, PROPS).orElseThrow();

		assertThat(window.moment())
				.isEqualTo(OffsetDateTime.of(fallBack, LocalTime.of(2, 30), ZoneOffset.ofHours(2)));
	}

	@Test
	void usesTheLocalMorningOnADaylightSavingDay() {
		LocalDate springForward = LocalDate.of(2026, 3, 29);

		ReminderMoment.Window window = ReminderMoment.of(springForward, null, null, PROPS).orElseThrow();

		assertThat(window.moment())
				.isEqualTo(OffsetDateTime.of(springForward, LocalTime.of(8, 0), ZoneOffset.ofHours(2)));
	}

	private static OffsetDateTime momentOf(LocalTime time, ReminderLead lead) {
		return ReminderMoment.of(DAY, time, lead, PROPS).orElseThrow().moment();
	}

	private static OffsetDateTime warsaw(LocalDate day, int hour, int minute) {
		return day.atTime(hour, minute).atZone(UserZone.ZONE).toOffsetDateTime();
	}
}
