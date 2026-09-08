package com.thedariusz.todoai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message a user actually reads. Two things are worth pinning and nothing else is: the code is
 * in the subject line — on a phone the notification is often the only place it is read — and the
 * body says how long it lasts, in step with the constant that decides.
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
