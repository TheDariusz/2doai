package com.thedariusz.todoai.auth;

/**
 * The message that carries a verification code, in one of the two languages (DEV-51).
 *
 * <p>Locale-bound for the same reason {@code ProposalTemplatePl} is: what these two methods return
 * <em>is</em> what the user reads, so a second locale is a second implementation
 * ({@link VerificationEmailEn}) rather than a branch inside this one.
 *
 * <p>The code is in the subject as well as the body, because on a phone the notification is often
 * the only place it is read. There is no link: a confirmation link in an email is a click an
 * attacker can make on the user's behalf, and six digits typed into the tab that is already open
 * cost the user nothing.
 */
final class VerificationEmailPl {

	private VerificationEmailPl() {
	}

	static String subject(String code) {
		return "Kod potwierdzający: " + code;
	}

	static String body(String code) {
		return """
				Twój kod potwierdzający adres w 2do AI: %s

				Kod jest ważny przez %d minut. Jeśli to nie Ty zakładałeś konto, zignoruj tę wiadomość.
				""".formatted(code, EmailVerificationService.CODE_VALIDITY.toMinutes());
	}
}
