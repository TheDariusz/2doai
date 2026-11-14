package com.thedariusz.todoai.auth;

/**
 * The message that carries a code, in one of the two languages (DEV-51, passwordless sign-in).
 *
 * <p>Locale-bound for the same reason {@code ProposalTemplatePl} is: what these two methods return
 * <em>is</em> what the user reads, so a second locale is a second implementation
 * ({@link VerificationEmailEn}) rather than a branch inside this one.
 *
 * <p>The code is in the subject as well as the body, because on a phone the notification is often
 * the only place it is read. Both bodies carry a {@link SignInLink}: plain text for clients that will
 * not render HTML, and an HTML alternative with the app's mark, a button and the code set large
 * ({@link VerificationEmailLayout}, DEV-62). The copy does not assume a sign-up: the same message
 * serves a first code and every sign-in after it.
 */
final class VerificationEmailPl {

	private VerificationEmailPl() {
	}

	static String subject(String code) {
		return "Kod do 2do AI: " + code;
	}

	static String body(String code, String link) {
		return """
				Twój kod do 2do AI: %s

				Kliknij link, żeby się zalogować:
				%s

				Albo wpisz kod w aplikacji. Jest ważny przez %d minut i działa tylko raz.

				Jeśli to nie Ty prosisz o kod, zignoruj tę wiadomość.
				""".formatted(code, link, minutes());
	}

	static String html(String code, String link, String baseUrl) {
		return VerificationEmailLayout.render(new VerificationEmailLayout.Copy(
				"pl",
				"Twój kod: %s · ważny %d minut".formatted(code, minutes()),
				"Zaloguj się do 2do AI",
				"Kliknij przycisk, żeby się zalogować.",
				"Zaloguj się",
				"Albo wpisz ten kod w aplikacji:",
				"Kod jest ważny przez %d minut i działa tylko raz.".formatted(minutes()),
				"Jeśli to nie Ty prosisz o kod, zignoruj tę wiadomość — bez kodu nikt nie zaloguje się na Twoje konto.",
				"2do AI · zadania, cele i marzenia w jednym miejscu"), code, link, baseUrl);
	}

	private static long minutes() {
		return EmailVerificationService.CODE_VALIDITY.toMinutes();
	}
}
