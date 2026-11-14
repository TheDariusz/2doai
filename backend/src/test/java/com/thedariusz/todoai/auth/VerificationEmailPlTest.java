package com.thedariusz.todoai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The message a user actually reads. Three things about it are load-bearing: the code is in the
 * subject line — on a phone the notification is often the only place it is read — the body says how
 * long the code lasts, in step with the constant that decides, and the body carries the link that
 * opens the code screen — in plain text and, for clients that render it, in an HTML alternative with
 * a button.
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

	@Test
	void htmlCarriesTheCodeTheButtonLinkAndTheValidity() {
		assertThat(VerificationEmailPl.html("04231789", LINK, "https://2doai.app"))
				.contains("04231789")
				.contains("href=\"https://2doai.app/verify#email=ala%40example.pl&amp;code=04231789\"")
				.contains(String.valueOf(EmailVerificationService.CODE_VALIDITY.toMinutes()))
				.contains("lang=\"pl\"");
	}

	@Test
	void htmlShowsTheAppLogoFromTheAppItself() {
		assertThat(VerificationEmailPl.html("04231789", LINK, "https://2doai.app/"))
				.contains("src=\"https://2doai.app/icon-192.png\"");
	}
}
