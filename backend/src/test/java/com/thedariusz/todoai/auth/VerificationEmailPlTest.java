package com.thedariusz.todoai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message a user actually reads. Three things about it are load-bearing: the code is in the
 * subject line — on a phone the notification is often the only place it is read — the body says how
 * long the code lasts, in step with the constant that decides, and the body carries the link that
 * prefills the code. The link opens a form and never signs anybody in on its own, so a mail scanner
 * that follows it spends nothing.
 */
class VerificationEmailPlTest {

	private static final String LINK = "https://2doai.app/verify#email=ala%40example.pl&code=04231789";

	@Test
	void carriesTheCodeInTheSubjectLine() {
		assertThat(VerificationEmailPl.subject("04231789")).contains("04231789");
	}

	@Test
	void namesHowLongTheCodeLasts() {
		assertThat(VerificationEmailPl.body("04231789", LINK))
				.contains("04231789")
				.contains(String.valueOf(EmailVerificationService.CODE_VALIDITY.toMinutes()));
	}

	@Test
	void carriesTheLinkThatPrefillsTheCode() {
		assertThat(VerificationEmailPl.body("04231789", LINK)).contains(LINK);
	}
}
