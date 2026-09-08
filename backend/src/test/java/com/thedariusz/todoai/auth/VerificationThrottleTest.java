package com.thedariusz.todoai.auth;

import java.time.Instant;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two rules that decide how often one address may be mailed a code, and the pruning that keeps
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
	 * The map is keyed on whatever address a caller typed, so an unbounded one is a memory-exhaustion
	 * lever on a 512 MB machine. Nothing sweeps it on a timer — a scheduled task would be one more
	 * thing running for the sake of a handful of entries — so every call prunes.
	 */
	@Test
	void forgetsAddressesWhoseSendsHaveRolledOffTheWindow() {
		throttle.admit(ADDRESS, NOON);
		assertThat(throttle.tracked()).isEqualTo(1);

		throttle.admit("ola@example.pl", NOON.plus(VerificationThrottle.WINDOW).plusSeconds(1));

		assertThat(throttle.tracked()).isEqualTo(1);
	}

	/** FR-019 — the account is erased, so the address starts again from nothing. */
	@Test
	void forgetsAnAddressOnRequest() {
		throttle.admit(ADDRESS, NOON);

		throttle.forget(ADDRESS);

		assertThat(throttle.admit(ADDRESS, NOON.plusSeconds(1))).isZero();
	}
}
