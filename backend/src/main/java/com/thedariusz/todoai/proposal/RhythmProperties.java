package com.thedariusz.todoai.proposal;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Positive;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the natural rhythm (S-05, FR-011) — the four numbers {@link ProposalRhythm}
 * draws inside. They are configuration rather than constants for one reason: whether the rhythm feels
 * like a friend or like a cron is a judgement a human makes over days of real use, and tuning it must
 * not be a code change.
 *
 * <p>Validated at startup, which matters more here than anywhere else in the app: these values are
 * read on a background thread days after boot, so an inverted interval would surface as an exception
 * in a log nobody is watching, on the one path whose whole job is to be noticed.
 *
 * @param minDays the shortest gap between two proposals
 * @param maxDays the longest; must not be below {@code minDays}
 * @param windowStartHour first hour of the user's day a proposal may land in, inclusive
 * @param windowEndHour the hour it must land before, exclusive — 21 means "never at 21:00 sharp"
 */
@ConfigurationProperties(prefix = "proposal.rhythm")
@Validated
public record RhythmProperties(@Positive int minDays, @Positive int maxDays,
		@Min(0) @Max(23) int windowStartHour, @Min(1) @Max(24) int windowEndHour) {

	@AssertTrue(message = "max-days must not be below min-days, and window-end-hour must be after window-start-hour")
	boolean isAWindowThatCanBeDrawnFrom() {
		return maxDays >= minDays && windowEndHour > windowStartHour;
	}
}
