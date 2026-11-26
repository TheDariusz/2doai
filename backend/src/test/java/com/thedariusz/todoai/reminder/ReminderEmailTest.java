package com.thedariusz.todoai.reminder;

import java.time.LocalDate;
import java.time.LocalTime;

import com.thedariusz.todoai.user.AppLanguage;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the due-date reminder says (DEV-63), in each account language: the entry, when it is due,
 * and the way back into the app. Assertions name the entry and the date parts rather than the copy
 * around them, so they hold when the wording is tuned.
 */
class ReminderEmailTest {

	private static final LocalDate DAY = LocalDate.of(2026, 12, 4);

	@Test
	void namesTheEntryInTheSubjectInBothLanguages() {
		assertThat(ReminderEmail.subject(AppLanguage.PL, "Zapłacić za prąd")).contains("Zapłacić za prąd");
		assertThat(ReminderEmail.subject(AppLanguage.EN, "Pay the power bill")).contains("Pay the power bill");
	}

	@Test
	void writesTheSubjectInTheAccountLanguage() {
		assertThat(ReminderEmail.subject(AppLanguage.PL, "x"))
				.isNotEqualTo(ReminderEmail.subject(AppLanguage.EN, "x"));
	}

	@Test
	void abbreviatesAnEntryFarTooLongToBeASubjectLine() {
		String subject = ReminderEmail.subject(AppLanguage.PL, "Zrobić " + "bardzo ".repeat(80) + "dużo");

		assertThat(subject).hasSizeLessThan(100).endsWith("...");
	}

	@Test
	void givesThePolishBodyTheEntryTheLocalizedDateTheTimeAndTheLink() {
		String body = ReminderEmail.body(AppLanguage.PL, "Zapłacić za prąd", DAY, LocalTime.of(12, 0),
				"https://2doai.app");

		assertThat(body).contains("Zapłacić za prąd", "4 grudnia 2026", "12:00", "https://2doai.app/goals");
	}

	@Test
	void givesTheEnglishBodyTheEntryTheLocalizedDateTheTimeAndTheLink() {
		String body = ReminderEmail.body(AppLanguage.EN, "Pay the power bill", DAY, LocalTime.of(9, 5),
				"https://2doai.app");

		assertThat(body).contains("Pay the power bill", "December 4, 2026", "09:05", "https://2doai.app/goals");
	}

	@Test
	void leavesTheTimeOutForADateOnlyTask() {
		String body = ReminderEmail.body(AppLanguage.EN, "Pay the power bill", DAY, null, "https://2doai.app");

		assertThat(body).contains("December 4, 2026").doesNotContainPattern("\\d{2}:\\d{2}");
	}

	@Test
	void survivesABaseUrlWithATrailingSlash() {
		String body = ReminderEmail.body(AppLanguage.PL, "x", DAY, null, "https://2doai.app/");

		assertThat(body).contains("https://2doai.app/goals").doesNotContain("//goals");
	}
}
