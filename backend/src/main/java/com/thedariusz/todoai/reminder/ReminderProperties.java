package com.thedariusz.todoai.reminder;

import java.time.Duration;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The two numbers the due-date reminder (DEV-63) runs on, validated at startup in the
 * {@code RhythmProperties} mould: they are read on a background thread days after boot, where a bad
 * value would fail in a log nobody is watching.
 *
 * @param morningHour the hour of the user's day a date-only task, or a {@code MORNING_OF} lead, is
 *        reminded at
 * @param lateGrace how long past its due moment a reminder may still go out. It must outlast the
 *        longest the shared scheduler thread can be held: a proposal fire can block it for about two
 *        and a half minutes ({@code spring.ai.openai.timeout} × two attempts, plus the three 10 s
 *        {@code spring.mail} timeouts). Re-check the 4-minute floor whenever those change.
 */
@ConfigurationProperties(prefix = "reminder")
@Validated
public record ReminderProperties(@Min(0) @Max(23) int morningHour, @NotNull Duration lateGrace) {

	/** See {@code lateGrace}: the tick may be blocked this long by a proposal fire. */
	static final Duration MIN_LATE_GRACE = Duration.ofMinutes(4);

	@AssertTrue(message = "late-grace must be at least PT4M, or a tick held up by a proposal fire loses reminders")
	boolean isAGraceThatOutlastsABlockedTick() {
		return lateGrace == null || lateGrace.compareTo(MIN_LATE_GRACE) >= 0;
	}
}
