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

				Click the link to sign in:
				%s

				Or type the code into the app. It is valid for %d minutes and works once.

				If you did not ask for a code, ignore this message.
				""".formatted(code, link, minutes());
	}

	static String html(String code, String link, String baseUrl) {
		return VerificationEmailLayout.render(new VerificationEmailLayout.Copy(
				"en",
				"Your code: %s · valid for %d minutes".formatted(code, minutes()),
				"Sign in to 2do AI",
				"Press the button to sign in.",
				"Sign in",
				"Or type this code into the app:",
				"The code is valid for %d minutes and works once.".formatted(minutes()),
				"If you did not ask for a code, ignore this message — nobody can sign in to your account without it.",
				"2do AI · tasks, goals and dreams in one place"), code, link, baseUrl);
	}

	private static long minutes() {
		return EmailVerificationService.CODE_VALIDITY.toMinutes();
	}
}
