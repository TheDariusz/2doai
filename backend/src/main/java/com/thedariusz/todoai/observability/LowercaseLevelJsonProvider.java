package com.thedariusz.todoai.observability;

import java.util.Locale;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.loki4j.logback.json.JsonEventWriter;
import com.github.loki4j.logback.json.LogLevelJsonProvider;

/**
 * The {@code level} key of the JSON line, lowercase so that it matches the label of the same name
 * ({@link LowercaseLevelConverter}). loki4j's own provider writes Logback's uppercase spelling.
 */
public class LowercaseLevelJsonProvider extends LogLevelJsonProvider {

	@Override
	protected void writeExactlyOneField(JsonEventWriter writer, ILoggingEvent event) {
		writer.writeStringField(getFieldName(), event.getLevel().toString().toLowerCase(Locale.ROOT));
	}

}
