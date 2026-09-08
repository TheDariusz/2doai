package com.thedariusz.todoai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message a user actually reads. Three things about it are load-bearing: the code is in the
 * subject line — on a phone the notification is often the only place it is read — the body says how
 * long the code lasts, in step with the constant that decides, and there is nothing to click. The
 * last of those is a security property, not an omission: a confirmation link is a click somebody else
 * can make on the user's behalf.
 */
class VerificationEmailPlTest {

	@Test
	void carriesTheCodeInTheSubjectLine() {
		assertThat(VerificationEmailPl.subject("123456")).contains("123456");
	}

	@Test
	void namesHowLongTheCodeLasts() {
		assertThat(VerificationEmailPl.body("123456"))
				.contains("123456")
				.contains(String.valueOf(EmailVerificationService.CODE_VALIDITY.toMinutes()));
	}

	/** Answering happens in the app, and a link in a message is a click somebody else can make. */
	@Test
	void offersNoLinkToClick() {
		assertThat(VerificationEmailPl.body("123456")).doesNotContain("http");
	}
}
