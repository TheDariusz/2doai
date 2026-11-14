package com.thedariusz.todoai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link VerificationEmailPlTest}'s sibling, for the sibling implementation. */
class VerificationEmailEnTest {

	private static final String LINK = "https://2doai.app/verify#email=ala%40example.pl&code=04231789";

	@Test
	void carriesTheCodeInTheSubjectLine() {
		assertThat(VerificationEmailEn.subject("04231789")).contains("04231789");
	}

	@Test
	void namesHowLongTheCodeLasts() {
		assertThat(VerificationEmailEn.body("04231789", LINK))
				.contains("04231789")
				.contains(String.valueOf(EmailVerificationService.CODE_VALIDITY.toMinutes()));
	}

	@Test
	void carriesTheLinkThatPrefillsTheCode() {
		assertThat(VerificationEmailEn.body("04231789", LINK)).contains(LINK);
	}
}
