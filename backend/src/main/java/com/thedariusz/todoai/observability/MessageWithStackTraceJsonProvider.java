package com.thedariusz.todoai.observability;

import ch.qos.logback.classic.pattern.ExtendedThrowableProxyConverter;
import ch.qos.logback.classic.pattern.ThrowableHandlingConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.loki4j.logback.json.JsonEventWriter;
import com.github.loki4j.logback.json.MessageJsonProvider;

/**
 * The {@code msg} key of the JSON line: the formatted message and, when an exception was logged, its
 * full stack trace on the following lines. The shared logging contract keeps stack traces in the
 * message body so that {@code err} stays a short, matchable summary ({@link ErrorSummaryJsonProvider}).
 */
public class MessageWithStackTraceJsonProvider extends MessageJsonProvider {

	private final ThrowableHandlingConverter stackTrace = new ExtendedThrowableProxyConverter();

	@Override
	public void start() {
		stackTrace.start();
		super.start();
	}

	@Override
	public void stop() {
		stackTrace.stop();
		super.stop();
	}

	@Override
	protected void writeExactlyOneField(JsonEventWriter writer, ILoggingEvent event) {
		String message = event.getFormattedMessage();
		if (event.getThrowableProxy() != null) {
			message = message + "\n" + stackTrace.convert(event).stripTrailing();
		}
		writer.writeStringField(getFieldName(), message);
	}

}
