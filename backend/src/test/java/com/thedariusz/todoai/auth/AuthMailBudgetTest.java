package com.thedariusz.todoai.auth;

import java.time.Duration;
import java.time.Instant;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The daily ceiling on auth mail: admit, exhaust, refund, and the day rolling over. The day is the
 * provider's quota day (UTC), not the user's.
 */
class AuthMailBudgetTest {

	private static final Instant MORNING = Instant.parse("2026-11-14T08:00:00Z");

	@Test
	void admitsUpToTheBudgetAndRefusesTheNextOne() {
		AuthMailBudget budget = new AuthMailBudget(3);

		assertThat(budget.admit(MORNING)).isTrue();
		assertThat(budget.admit(MORNING)).isTrue();
		assertThat(budget.admit(MORNING)).isTrue();

		assertThat(budget.admit(MORNING)).isFalse();
	}

	/** A send the provider would not take spent nothing of the quota, so it gives its place back. */
	@Test
	void givesBackTheSendThatNeverHappened() {
		AuthMailBudget budget = new AuthMailBudget(1);
		budget.admit(MORNING);

		budget.refund(MORNING);

		assertThat(budget.admit(MORNING)).isTrue();
	}

	@Test
	void neverRefundsBelowZero() {
		AuthMailBudget budget = new AuthMailBudget(1);

		budget.refund(MORNING);
		budget.admit(MORNING);

		assertThat(budget.admit(MORNING)).as("a refund with nothing spent is not credit").isFalse();
	}

	@Test
	void startsAFreshBudgetWhenTheUtcDayRollsOver() {
		AuthMailBudget budget = new AuthMailBudget(1);
		budget.admit(Instant.parse("2026-11-14T23:59:59Z"));

		assertThat(budget.admit(Instant.parse("2026-11-15T00:00:00Z"))).isTrue();
	}

	/**
	 * Requests are stamped on the request thread and admitted later on the auth-mail executor, so one
	 * stamped before midnight can be admitted after one stamped after it. The day only moves forward:
	 * the late one is charged to today, and today's count is not reset.
	 */
	@Test
	void chargesALateArrivalFromYesterdayToTodayInsteadOfRollingBack() {
		AuthMailBudget budget = new AuthMailBudget(1);
		assertThat(budget.admit(Instant.parse("2026-11-15T00:00:01Z"))).isTrue();

		assertThat(budget.admit(Instant.parse("2026-11-14T23:59:59Z"))).isFalse();
		assertThat(budget.admit(Instant.parse("2026-11-15T00:00:02Z"))).isFalse();
	}

	/** Yesterday's failed send is not today's credit. */
	@Test
	void ignoresARefundForAPreviousDay() {
		AuthMailBudget budget = new AuthMailBudget(1);
		Instant yesterday = MORNING.minus(Duration.ofDays(1));
		budget.admit(yesterday);
		budget.admit(MORNING);

		budget.refund(yesterday);

		assertThat(budget.admit(MORNING)).isFalse();
	}

	/**
	 * The owner learns the budget ran out from one log line a day, not one per refused request — a
	 * flood would otherwise write as many lines as it sends requests.
	 */
	@Test
	void logsTheExhaustionOncePerDay() {
		AuthMailBudget budget = new AuthMailBudget(1);
		ListAppender<ILoggingEvent> lines = new ListAppender<>();
		Logger log = (Logger) LoggerFactory.getLogger(AuthMailBudget.class);
		lines.start();
		log.addAppender(lines);
		try {
			budget.admit(MORNING);
			budget.admit(MORNING);
			budget.admit(MORNING.plusSeconds(60));
			budget.admit(MORNING.plus(Duration.ofDays(1)));
			budget.admit(MORNING.plus(Duration.ofDays(1)));
		}
		finally {
			log.detachAppender(lines);
		}

		assertThat(lines.list)
				.filteredOn(event -> event.getKeyValuePairs() != null && event.getKeyValuePairs().stream()
						.anyMatch(kv -> kv.key.equals("event") && kv.value.equals("auth_mail_budget_exhausted")))
				.hasSize(2);
	}
}
