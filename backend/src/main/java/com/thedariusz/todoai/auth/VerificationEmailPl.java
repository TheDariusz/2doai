package com.thedariusz.todoai.auth;

/**
 * The message that carries a code, in one of the two languages (DEV-51, passwordless sign-in).
 *
 * <p>Locale-bound for the same reason {@code ProposalTemplatePl} is: what these two methods return
 * <em>is</em> what the user reads, so a second locale is a second implementation
 * ({@link VerificationEmailEn}) rather than a branch inside this one.
 *
 * <p>The code is in the subject as well as the body, because on a phone the notification is often
 * the only place it is read. The body also carries a {@link SignInLink}, which opens the code screen
 * with the code filled in. The link spends nothing on its own — the user still presses the button —
 * so a mail scanner that follows it signs nobody in. The copy does not assume a sign-up: the same
 * message serves a first code and every sign-in after it.
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

				Wpisz go w aplikacji albo otwórz ten link, który uzupełni go za Ciebie:
				%s

				Kod jest ważny przez %d minut. Jeśli ta wiadomość nie jest dla Ciebie, zignoruj ją.
				""".formatted(code, link, EmailVerificationService.CODE_VALIDITY.toMinutes());
	}
}
