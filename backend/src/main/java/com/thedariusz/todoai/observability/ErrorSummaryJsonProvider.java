package com.thedariusz.todoai.observability;

import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.classic.spi.IThrowableProxy;
import com.github.loki4j.logback.json.JsonEventWriter;
import com.github.loki4j.logback.json.StackTraceJsonProvider;

/**
 * The {@code err} key of the JSON line: the logged exception's type and message on one line, as the
 * shared logging contract defines it ({@code java.lang.IllegalStateException: boom}), so that a query
 * or alert rule can match it. The stack trace, causes included, goes in {@code msg} instead
 * ({@link MessageWithStackTraceJsonProvider}). Written only when something was thrown.
 *
 * <p>Extends loki4j's stack-trace provider only because that is the type its {@code JsonLayout} slot
 * accepts; the inherited {@code canWrite} is what keeps the key absent on a plain line.
 */
public class ErrorSummaryJsonProvider extends StackTraceJsonProvider {

	public ErrorSummaryJsonProvider() {
		setFieldName("err");
	}

	@Override
	protected void writeExactlyOneField(JsonEventWriter writer, ILoggingEvent event) {
		IThrowableProxy thrown = event.getThrowableProxy();
		String message = thrown.getMessage();
		writer.writeStringField(getFieldName(),
				message == null ? thrown.getClassName() : thrown.getClassName() + ": " + message);
	}

}
