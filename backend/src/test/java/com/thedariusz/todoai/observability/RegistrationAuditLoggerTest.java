package com.thedariusz.todoai.observability;

import java.util.UUID;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.thedariusz.todoai.user.UserVerified;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.groups.Tuple.tuple;

class RegistrationAuditLoggerTest {

	@Test
	void logsUserRegisteredWithTheUserId() {
		UUID id = UUID.randomUUID();
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		Logger logger = (Logger) LoggerFactory.getLogger(RegistrationAuditLogger.class);
		logger.addAppender(appender);
		appender.start();

		try {
			new RegistrationAuditLogger().onVerified(new UserVerified(id));
		}
		finally {
			logger.detachAppender(appender);
		}

		assertThat(appender.list).singleElement().satisfies(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.INFO);
			assertThat(event.getFormattedMessage()).isEqualTo("user registered");
			assertThat(event.getKeyValuePairs()).extracting(kv -> kv.key, kv -> String.valueOf(kv.value))
				.containsExactly(tuple("event", "user_registered"),
						tuple("user_id", id.toString()));
		});
	}

	@Test
	void isAnAfterCommitListener() throws NoSuchMethodException {
		// A verification that rolls back after UserVerified was published must not page the owner.
		TransactionalEventListener listener = RegistrationAuditLogger.class
			.getDeclaredMethod("onVerified", UserVerified.class)
			.getAnnotation(TransactionalEventListener.class);

		assertThat(listener).isNotNull();
		assertThat(listener.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
	}


}
