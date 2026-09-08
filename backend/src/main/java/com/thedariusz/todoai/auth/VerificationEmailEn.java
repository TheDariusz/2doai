package com.thedariusz.todoai.auth;

/**
 * {@link VerificationEmailPl}'s sibling for the other language — the second implementation its
 * javadoc calls for, sharing the shape of the message and none of its wording.
 */
final class VerificationEmailEn {

	private VerificationEmailEn() {
	}

	static String subject(String code) {
		return "Your confirmation code: " + code;
	}

	static String body(String code) {
		return """
				Your 2do AI confirmation code: %s

				The code is valid for %d minutes. If you did not sign up, ignore this message.
				""".formatted(code, EmailVerificationService.CODE_VALIDITY.toMinutes());
	}
}
