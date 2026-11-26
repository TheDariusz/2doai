package com.thedariusz.todoai.reminder;

import java.time.Duration;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link ReminderProperties} binds from the committed {@code application.properties}, and refuses
 * the two values that would only fail days later on the scheduler thread. Mirrors
 * {@code RhythmPropertiesTest}.
 */
@SpringJUnitConfig(ReminderPropertiesTest.Config.class)
@TestPropertySource(locations = "classpath:application.properties")
class ReminderPropertiesTest {

	@Autowired
	ReminderProperties properties;

	@Test
	void bindsTheMorningHourAndTheGraceFromApplicationProperties() {
		assertThat(properties.morningHour()).isEqualTo(8);
		assertThat(properties.lateGrace()).isEqualTo(Duration.ofMinutes(5));
	}

	@Test
	void rejectsAMorningHourOutsideTheDay() {
		bindingOf("reminder.morning-hour=24")
				.run(context -> assertThat(context).hasFailed()
						.getFailure().hasStackTraceContaining("morningHour"));
	}

	/**
	 * The grace is what lets an at-the-time reminder fire at all, and the tick can be held up for about
	 * two and a half minutes by a proposal fire on the same thread. Anything shorter loses reminders.
	 */
	@Test
	void rejectsAGraceShorterThanABlockedTick() {
		bindingOf("reminder.late-grace=PT1M")
				.run(context -> assertThat(context).hasFailed()
						.getFailure().hasStackTraceContaining("late-grace"));
	}

	private static ApplicationContextRunner bindingOf(String... overrides) {
		return new ApplicationContextRunner()
				.withUserConfiguration(Config.class)
				.withPropertyValues("reminder.morning-hour=8", "reminder.late-grace=PT5M")
				.withPropertyValues(overrides);
	}

	@Configuration
	@EnableConfigurationProperties(ReminderProperties.class)
	static class Config {
	}
}
