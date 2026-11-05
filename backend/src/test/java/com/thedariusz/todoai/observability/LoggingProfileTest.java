package com.thedariusz.todoai.observability;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.LoggerContext;
import com.github.loki4j.logback.Loki4jAppender;
import com.thedariusz.todoai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Outside the {@code prod} profile nothing may talk to Loki: CI and every {@code @SpringBootTest}
 * run with the console appender only, so no test run ever pushes a line to Grafana Cloud.
 */
@SpringBootTest
@Import(TestcontainersConfiguration.class)
class LoggingProfileTest {

	@Test
	void noLokiAppenderIsAttachedOutsideTheProdProfile() {
		Logger root = ((LoggerContext) LoggerFactory.getILoggerFactory()).getLogger(Logger.ROOT_LOGGER_NAME);

		assertThat(root.iteratorForAppenders()).toIterable()
			.isNotEmpty()
			.noneMatch(Loki4jAppender.class::isInstance);
	}

}
