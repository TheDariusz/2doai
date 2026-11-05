package com.thedariusz.todoai.observability;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;

import static org.assertj.core.api.Assertions.assertThat;

class HeartbeatTest {

	@Test
	void beatLogsTheHeartbeatEvent() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		Logger logger = (Logger) LoggerFactory.getLogger(Heartbeat.class);
		logger.addAppender(appender);
		appender.start();

		try {
			new Heartbeat().beat();
		}
		finally {
			logger.detachAppender(appender);
		}

		assertThat(appender.list).singleElement().satisfies(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.INFO);
			assertThat(event.getFormattedMessage()).isEqualTo("heartbeat");
			assertThat(event.getKeyValuePairs()).singleElement().satisfies(kv -> {
				assertThat(kv.key).isEqualTo("event");
				assertThat(kv.value).isEqualTo("heartbeat");
			});
		});
	}

	@Test
	void beatIsScheduledEveryFiveMinutes() throws NoSuchMethodException {
		Scheduled scheduled = Heartbeat.class.getDeclaredMethod("beat").getAnnotation(Scheduled.class);

		assertThat(scheduled).isNotNull();
		assertThat(scheduled.fixedRate()).isEqualTo(300_000);
	}

}
