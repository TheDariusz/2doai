package com.thedariusz.todoai.proposal;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.Random;

/**
 * When the app comes back on its own (S-05, FR-011) — the natural rhythm as a pure function, in the
 * {@link ProposalSelector} mould: no clock, no database, no scheduler. The moment to draw from and
 * the source of randomness both arrive as arguments, which is what lets a random product feel be
 * tested at all.
 *
 * <p><b>Two independent draws, and that is the product rule.</b> First a day, uniformly inside the
 * configured interval (2-7 days); then a second, uniformly inside the configured hours of that day.
 * A fixed interval, or a fixed time of day, would be a cron — and "an app that behaves like a cron"
 * is the failure mode the PRD's guardrail names. Drawing the time separately from the day is what
 * stops the rhythm from settling into "always around 14:30".
 *
 * <p><b>{@link Random}, not {@code SecureRandom}.</b> The unpredictability here is a feel, not a
 * secret: nothing an attacker could do with the next fire time is worth a blocking entropy source on
 * a background thread. Injected rather than held statically so a seeded instance makes the scheduler
 * tests state a moment instead of tolerating a range.
 *
 * <p>The window is applied in {@link #USER_ZONE}, because 9:00-21:00 is a claim about the
 * <em>user's</em> day. On a UTC server the naive reading lands an email at 08:00 in summer — the one
 * thing the window exists to prevent.
 */
final class ProposalRhythm {

	// ponytail: one hardcoded zone while every account is Polish; a user.timezone column is the
	// upgrade, and this constant is the only place the app decides whose day it means — both the
	// window below and ProposalService's clock reads go through it.
	static final ZoneId USER_ZONE = ZoneId.of("Europe/Warsaw");

	private ProposalRhythm() {
	}

	/**
	 * @param from the moment the rhythm moves on from — the fire that just happened, or boot for a
	 *        user who has never been scheduled
	 * @return the next moment to return to the user, inside the configured interval and window
	 */
	static OffsetDateTime next(OffsetDateTime from, RhythmProperties props, Random random) {
		LocalDate day = from.atZoneSameInstant(USER_ZONE)
				.toLocalDate()
				// Bound is exclusive, so the configured maximum has to be reachable.
				.plusDays(random.nextInt(props.minDays(), props.maxDays() + 1));

		int windowSeconds = (props.windowEndHour() - props.windowStartHour()) * 3600;
		LocalTime time = LocalTime.of(props.windowStartHour(), 0)
				.plusSeconds(random.nextInt(windowSeconds));

		// atZone rather than atOffset: on the two days a year the local clock jumps, a fixed offset
		// would be an hour wrong and a skipped local time would not exist at all. ZonedDateTime
		// resolves both — and the window starts long after either transition, so it never shows.
		return day.atTime(time).atZone(USER_ZONE).toOffsetDateTime();
	}

	/**
	 * Whether a moment falls inside the hours a proposal may land in. Every moment {@link #next} draws
	 * does by construction — this is asked of the ones that arrive <em>late</em>: a machine that was
	 * down over the whole window (a deploy, a Fly host migration, an OOM restart) comes back holding a
	 * fire time already in the past, and 04:00 is not when a friend makes up for a quiet day.
	 */
	static boolean isInsideWindow(OffsetDateTime at, RhythmProperties props) {
		int hour = at.atZoneSameInstant(USER_ZONE).getHour();
		return hour >= props.windowStartHour() && hour < props.windowEndHour();
	}
}
