package com.thedariusz.todoai.reminder;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.time.format.FormatStyle;

import com.thedariusz.todoai.user.AppLanguage;
import org.apache.commons.lang3.StringUtils;

/**
 * What a due-date reminder says (DEV-63): the entry, when it is due, and the way back into the app.
 * Plain text, like the proposal mail, and with no action links — completing or moving the task
 * happens in the app, logged in, so a forwarded message is never an unauthenticated write.
 *
 * <p>Locale-bound because what it returns <em>is</em> what the user reads; each language is one
 * branch of each switch, and the date is formatted in the account's locale.
 */
final class ReminderEmail {

	/** As in {@code ProposalEmail}: a mail client shows perhaps sixty characters of a subject. */
	private static final int SUBJECT_ENTRY_LIMIT = 60;

	private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

	private ReminderEmail() {
	}

	/** Names the entry, so the inbox row is readable without opening it. */
	static String subject(AppLanguage language, String content) {
		String entry = StringUtils.abbreviate(content, SUBJECT_ENTRY_LIMIT);
		return switch (language) {
			case PL -> "Przypomnienie: " + entry;
			case EN -> "Reminder: " + entry;
		};
	}

	/** @param dueTime null for a date-only task, which then shows its date alone */
	static String body(AppLanguage language, String content, LocalDate dueDate, LocalTime dueTime,
			String baseUrl) {
		String due = DateTimeFormatter.ofLocalizedDate(FormatStyle.FULL).withLocale(language.locale())
				.format(dueDate) + (dueTime == null ? "" : ", " + TIME.format(dueTime));
		String goals = StringUtils.removeEnd(baseUrl, "/") + "/goals";
		return switch (language) {
			case PL -> """
					%s

					Termin: %s

					Zobacz w aplikacji: %s
					""".formatted(content, due, goals);
			case EN -> """
					%s

					Due: %s

					Open it in the app: %s
					""".formatted(content, due, goals);
		};
	}
}
