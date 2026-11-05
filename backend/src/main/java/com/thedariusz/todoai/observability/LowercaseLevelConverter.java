package com.thedariusz.todoai.observability;

import java.util.Locale;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;

/**
 * {@code %lowerLevel}: the event's level as the shared Loki contract spells the {@code level} label,
 * {@code info} rather than Logback's {@code INFO}. Neither Logback nor loki4j can lowercase a
 * pattern on its own, and a label is case-sensitive in every LogQL selector.
 */
public class LowercaseLevelConverter extends ClassicConverter {

	@Override
	public String convert(ILoggingEvent event) {
		return event.getLevel().toString().toLowerCase(Locale.ROOT);
	}

}
