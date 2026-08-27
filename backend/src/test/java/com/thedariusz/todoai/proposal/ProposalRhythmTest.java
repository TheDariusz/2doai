package com.thedariusz.todoai.proposal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZonedDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Random;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The natural rhythm as a pure function, asserted the way {@code ProposalSelectorTest} asserts the
 * selection heuristic: no container, no clock, no scheduler. Everything it needs arrives as an
 * argument, {@link Random} included — which is the only reason a random product feel can be pinned
 * down by a test at all.
 *
 * <p><b>What is actually guaranteed</b> is the envelope, not the sequence: the draw lands inside the
 * configured day interval and inside the configured hours of the user's day. That the sequence
 * <em>feels</em> like a friend rather than a cron is a judgement a human makes over days — the
 * parameters live in {@code RhythmProperties} precisely so it can be tuned without touching this.
 */
class ProposalRhythmTest {

	private static final RhythmProperties PROPS = new RhythmProperties(2, 7, 9, 21);

	/** A Tuesday afternoon in Warsaw, so the drawn days are unambiguous to read. */
	private static final OffsetDateTime FROM = OffsetDateTime.parse("2026-08-25T14:30:00+02:00");

	/** Enough draws that a bound broken by one would have to be improbably lucky to survive. */
	private static final int MANY = 500;

	private static ZonedDateTime warsaw(OffsetDateTime moment) {
		return moment.atZoneSameInstant(ProposalRhythm.USER_ZONE);
	}

	@Test
	void landsBetweenTwoAndSevenDaysAhead() {
		Random random = new Random(20260825L);
		LocalDate today = warsaw(FROM).toLocalDate();

		IntStream.range(0, MANY).forEach(draw -> {
			LocalDate day = warsaw(ProposalRhythm.next(FROM, PROPS, random)).toLocalDate();

			assertThat(ChronoUnit.DAYS.between(today, day))
					.as("PRD guardrail: ~1 proposal per 2-7 days, never a fixed interval")
					.isBetween(2L, 7L);
		});
	}

	@Test
	void landsInsideTheUsersWakingHours() {
		Random random = new Random(20260825L);

		IntStream.range(0, MANY).forEach(draw -> {
			ZonedDateTime next = warsaw(ProposalRhythm.next(FROM, PROPS, random));

			// The window is half-open: 21:00 sharp is already outside it, so the upper bound is the
			// last second before it rather than the hour itself.
			assertThat(next.getHour()).isBetween(9, 20);
		});
	}

	@Test
	void spreadsAcrossTheWholeIntervalRatherThanRepeatingOneOffset() {
		Random random = new Random(20260825L);
		LocalDate today = warsaw(FROM).toLocalDate();

		// A rhythm that always picked the same day would satisfy both bounds above and be exactly the
		// cron the product is not; five distinct days out of six possible is the cheapest way to say so.
		assertThat(IntStream.range(0, MANY)
				.mapToObj(draw -> warsaw(ProposalRhythm.next(FROM, PROPS, random)).toLocalDate())
				.map(day -> ChronoUnit.DAYS.between(today, day))
				.distinct()
				.count())
				.isGreaterThanOrEqualTo(5);
	}

	/**
	 * The draw is a product feel, not a security property, so a seeded {@link Random} is enough — and
	 * the seed is what lets the scheduler's own tests state a fire time instead of tolerating a range.
	 */
	@Test
	void drawsTheSameMomentTwiceForTheSameSeed() {
		OffsetDateTime once = ProposalRhythm.next(FROM, PROPS, new Random(42));
		OffsetDateTime twice = ProposalRhythm.next(FROM, PROPS, new Random(42));

		assertThat(once).isEqualTo(twice);
	}

	/**
	 * The window is the <em>user's</em> day, so it has to be read in their zone rather than the
	 * server's. On a UTC machine the naive reading is an hour or two off — enough to email someone at
	 * 08:00, which is precisely the thing 9:00-21:00 exists to prevent.
	 */
	@Test
	void readsTheWindowInTheUsersZoneRatherThanTheServersDefault() {
		Random random = new Random(7);

		// The same instant, expressed in a zone eleven hours away: the draw must not move with it.
		OffsetDateTime elsewhere = FROM.withOffsetSameInstant(java.time.ZoneOffset.ofHours(-9));

		assertThat(warsaw(ProposalRhythm.next(elsewhere, PROPS, new Random(7))))
				.isEqualTo(warsaw(ProposalRhythm.next(FROM, PROPS, random)));
	}

	/**
	 * A day the user's clock skips: 2027-03-28 has no 02:00-03:00 in Warsaw. The window starts well
	 * after the gap, so nothing should be clever here — but a draw that produced a nonexistent local
	 * time would be an exception on a background thread days later, so it is pinned rather than
	 * assumed.
	 */
	@Test
	void survivesTheSpringForwardWeekend() {
		OffsetDateTime before = OffsetDateTime.parse("2027-03-24T14:30:00+01:00");
		Random random = new Random(99);

		IntStream.range(0, MANY).forEach(draw -> {
			ZonedDateTime next = warsaw(ProposalRhythm.next(before, PROPS, random));

			assertThat(next.getHour()).isBetween(9, 20);
		});
	}
}
