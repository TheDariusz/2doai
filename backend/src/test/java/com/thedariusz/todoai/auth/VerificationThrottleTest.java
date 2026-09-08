package com.thedariusz.todoai.auth;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two rules that decide how often one address may be mailed a code, and the ceiling that keeps
 * the map from being a memory lever. The clock is a parameter precisely so these can be stated
 * without waiting for any of it.
 */
class VerificationThrottleTest {

	private static final String ADDRESS = "ala@example.pl";

	private static final Instant NOON = Instant.parse("2026-09-08T12:00:00Z");

	private final VerificationThrottle throttle = new VerificationThrottle();

	@Test
	void admitsTheFirstCodeForAnAddress() {
		assertThat(throttle.admit(ADDRESS, NOON)).isZero();
	}

	@Test
	void refusesASecondCodeInsideTheCooldownAndSaysHowLongToWait() {
		throttle.admit(ADDRESS, NOON);

		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(20))).isEqualTo(40);
		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(60))).isZero();
	}

	/**
	 * The final sub-second of the cooldown, where an integer division is one rounding away from
	 * admitting: {@code Duration.toSeconds()} truncates, so half a second left reads as zero, and a zero
	 * falls straight through {@code admit}'s {@code if (cooldown > 0)} into an allowed send. Every other
	 * case here lands on a whole second and would never see it.
	 */
	@Test
	void roundsTheLastFractionOfTheCooldownUpRatherThanAdmitting() {
		throttle.admit(ADDRESS, NOON);

		assertThat(throttle.admit(ADDRESS, NOON.plus(VerificationThrottle.COOLDOWN).minusMillis(500)))
				.isEqualTo(1);
	}

	/** The key is the address registration stored, not the characters typed. */
	@Test
	void readsAPaddedMixedCaseAddressAsTheSameOne() {
		throttle.admit("  Ala@Example.PL ", NOON);

		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(1))).isEqualTo(59);
	}

	/** A send the provider would not take is owed nothing: the outage must not spend the budget. */
	@Test
	void refundsASendThatNeverHappened() {
		throttle.admit(ADDRESS, NOON);

		throttle.refund(ADDRESS, NOON);

		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(1))).isZero();
	}

	/**
	 * And it gives back <em>its own</em> send. Two can be in flight for one address at once — a sign-up
	 * and a "send again" from a second tab — so a refund that popped the tail would have the failing one
	 * cancel the send that actually arrived, leaving the address owed a cooldown nobody serves.
	 */
	@Test
	void refundsTheSendItWasGivenRatherThanTheNewestOne() {
		throttle.record(ADDRESS, NOON);
		throttle.record(ADDRESS, NOON.plusSeconds(1));

		throttle.refund(ADDRESS, NOON);

		// The cooldown still runs from the second send, not the first.
		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(2))).isEqualTo(59);
	}

	/** The registration path cannot be refused, but the code it sends still starts the cooldown. */
	@Test
	void recordsASendWithoutAskingFirst() {
		throttle.record(ADDRESS, NOON);

		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(20))).isEqualTo(40);
	}

	/** The cooldown is per address: one person pressing "send again" cannot slow anybody else down. */
	@Test
	void keepsOneAddressesCooldownToItself() {
		throttle.admit(ADDRESS, NOON);

		assertThat(throttle.admit("ola@example.pl", NOON)).isZero();
	}

	@Test
	void refusesASixthCodeInsideTheHour() {
		for (int minute = 0; minute < VerificationThrottle.MAX_PER_WINDOW; minute++) {
			assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(minute * 120L))).isZero();
		}

		// Past the cooldown, so it is the hourly ceiling refusing — and it waits for the first send
		// of the five to roll off the window, not for a fixed penalty.
		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(600))).isEqualTo(3000);
		assertThat(throttle.admit(ADDRESS, NOON.plus(VerificationThrottle.WINDOW))).isZero();
	}

	/**
	 * The map is keyed on whatever address a caller typed, on an endpoint anybody can post to, so an
	 * unbounded one is a memory-exhaustion lever on a 512 MB machine. Expiring entries is not a bound —
	 * it only says an address leaves an hour after its last send, and an hour of distinct addresses is
	 * exactly what a flood supplies — so the map has a hard ceiling and evicts to stay under it.
	 */
	@Test
	void neverRemembersMoreAddressesThanItsCeiling() {
		for (int address = 0; address <= VerificationThrottle.MAX_TRACKED; address++) {
			throttle.admit(address + "@example.pl", NOON);
		}

		assertThat(throttle.tracked()).isEqualTo(VerificationThrottle.MAX_TRACKED);
	}

	/**
	 * <em>Which</em> address is dropped is the safety property, not only how many. Eviction costs an
	 * address its cooldown, so it must fall on the one quiet longest — which, to be evicted, has been
	 * quiet while {@code MAX_TRACKED} others were not, far longer than the window it would have aged
	 * out of anyway. The address still being pressed keeps its limit, and that is the one that matters.
	 */
	@Test
	void evictsTheAddressQuietLongestAndKeepsTheBusyOne() {
		throttle.admit(ADDRESS, NOON);
		for (int address = 0; address < VerificationThrottle.MAX_TRACKED; address++) {
			throttle.admit(address + "@example.pl", NOON);
		}

		// The most recent address of the flood still owes its cooldown — asked first, because asking
		// about an evicted address re-admits it, and that is itself the eviction of the next one.
		assertThat(throttle.admit((VerificationThrottle.MAX_TRACKED - 1) + "@example.pl", NOON.plusSeconds(1)))
				.isEqualTo(59);
		// The one quiet longest was dropped, so it starts again from nothing.
		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(1))).isZero();
	}

	/** FR-019 — the account is erased, so the address starts again from nothing. */
	@Test
	void forgetsAnAddressOnRequest() {
		throttle.admit(ADDRESS, NOON);

		throttle.forget(ADDRESS);

		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(1))).isZero();
	}
}
