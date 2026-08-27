package com.thedariusz.todoai.proposal;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link RhythmProperties} binds the rhythm envelope from the committed
 * {@code application.properties} (relaxed binding of {@code proposal.rhythm.min-days} →
 * {@code minDays()}) — so a typo in a key fails the build rather than shipping a schedule drawn from
 * zeroes. Mirrors {@code MemoryPropertiesTest}; no auto-configuration runs, so no datasource is
 * needed.
 *
 * <p>The ordering rule is asserted too, because it is the one misconfiguration that would survive
 * startup and surface days later as an exception on a background thread — the least observable place
 * in the app.
 */
@SpringJUnitConfig(RhythmPropertiesTest.Config.class)
@TestPropertySource(locations = "classpath:application.properties")
class RhythmPropertiesTest {

	@Autowired
	RhythmProperties properties;

	@Test
	void bindsTheRhythmEnvelopeFromApplicationProperties() {
		assertThat(properties.minDays()).isEqualTo(2);
		assertThat(properties.maxDays()).isEqualTo(7);
		assertThat(properties.windowStartHour()).isEqualTo(9);
		assertThat(properties.windowEndHour()).isEqualTo(21);
	}

	@Test
	void rejectsAnIntervalThatCannotBeDrawnFrom() {
		bindingOf("proposal.rhythm.min-days=7", "proposal.rhythm.max-days=2")
				.run(context -> assertThat(context).hasFailed()
						.getFailure().hasStackTraceContaining("min-days"));
	}

	@Test
	void rejectsAWindowThatCannotBeDrawnFrom() {
		bindingOf("proposal.rhythm.window-start-hour=21", "proposal.rhythm.window-end-hour=9")
				.run(context -> assertThat(context).hasFailed()
						.getFailure().hasStackTraceContaining("window-start-hour"));
	}

	@Test
	void rejectsANonPositiveIntervalAtStartup() {
		bindingOf("proposal.rhythm.min-days=0")
				.run(context -> assertThat(context).hasFailed()
						.getFailure().hasStackTraceContaining("minDays"));
	}

	/** The committed values, with the ones under test overridden — a partial record cannot bind. */
	private static ApplicationContextRunner bindingOf(String... overrides) {
		return new ApplicationContextRunner()
				.withUserConfiguration(Config.class)
				.withPropertyValues("proposal.rhythm.min-days=2", "proposal.rhythm.max-days=7",
						"proposal.rhythm.window-start-hour=9", "proposal.rhythm.window-end-hour=21")
				.withPropertyValues(overrides);
	}

	@Configuration
	@EnableConfigurationProperties(RhythmProperties.class)
	static class Config {
	}
}
