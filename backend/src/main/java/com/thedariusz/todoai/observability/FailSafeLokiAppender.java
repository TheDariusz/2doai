package com.thedariusz.todoai.observability;

import com.github.loki4j.logback.Loki4jAppender;

/**
 * loki4j's appender, except that failing to start costs the Loki lines rather than the application.
 *
 * <p>Plain {@link Loki4jAppender#start()} throws on a push URL it cannot parse — an unset
 * {@code LOKI_PUSH_URL} renders as {@code LOKI_PUSH_URL_IS_UNDEFINED} and is one — and Spring Boot
 * refuses to start when Logback configuration fails. Here the failure becomes a WARN status (Boot
 * only aborts on ERROR), the appender stays stopped, and the console keeps every line. Nothing is
 * silently lost for long: no heartbeat reaches Loki, and the owner's absence alert fires within
 * fifteen minutes.
 */
public class FailSafeLokiAppender extends Loki4jAppender {

	@Override
	public void start() {
		try {
			super.start();
		}
		catch (RuntimeException e) {
			addWarn("Loki appender not started, log lines stay on the console only: " + e);
		}
	}

}
