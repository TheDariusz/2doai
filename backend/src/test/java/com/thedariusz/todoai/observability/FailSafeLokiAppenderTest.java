package com.thedariusz.todoai.observability;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.status.Status;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

/**
 * A Loki appender that cannot start must cost the Loki lines, not the application: Spring Boot
 * refuses to start when Logback configuration reports an ERROR status, and plain loki4j throws out of
 * {@code start()} on a push URL it cannot parse — an unset {@code LOKI_PUSH_URL} included.
 */
class FailSafeLokiAppenderTest {

	@Test
	void anUnusablePushUrlLeavesTheAppenderStoppedWithAWarningAndNoError() {
		LoggerContext context = new LoggerContext();
		context.putProperty("LOKI_PUSH_URL", "not a url");
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(context);

		assertThatCode(() -> configurator.doConfigure(
				getClass().getResource("/observability/loki-appender-under-test.xml")))
			.doesNotThrowAnyException();

		assertThat(context.getLogger("ROOT").getAppender("LOKI").isStarted()).isFalse();
		assertThat(context.getStatusManager().getCopyOfStatusList())
			.noneMatch(status -> status.getLevel() == Status.ERROR)
			.anyMatch(status -> status.getLevel() == Status.WARN
					&& status.getMessage().contains("Loki appender not started"));
		assertThatCode(() -> context.getLogger("probe").info("still logging")).doesNotThrowAnyException();
		context.stop();
	}

}
