package com.thedariusz.todoai.auth;

/**
 * {@link VerificationEmailPl}'s sibling for the other language — the second implementation its
 * javadoc calls for, sharing the shape of the message and none of its wording.
 */
final class VerificationEmailEn {

	private VerificationEmailEn() {
	}

	static String subject(String code) {
		return "Your 2do AI code: " + code;
	}

	static String body(String code, String link) {
		return """
				Your 2do AI code: %s

				Type it into the app, or open this link to have it filled in for you:
				%s

				The code is valid for %d minutes. If you did not ask for it, ignore this message.
				""".formatted(code, link, EmailVerificationService.CODE_VALIDITY.toMinutes());
	}
}
