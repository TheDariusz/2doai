package com.thedariusz.todoai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/** {@link VerificationEmailPlTest}'s sibling, for the sibling implementation. */
class VerificationEmailEnTest {

	@Test
	void carriesTheCodeInTheSubjectLine() {
		assertThat(VerificationEmailEn.subject("123456")).contains("123456");
	}

	@Test
	void namesHowLongTheCodeLasts() {
		assertThat(VerificationEmailEn.body("123456"))
				.contains("123456")
				.contains(String.valueOf(EmailVerificationService.CODE_VALIDITY.toMinutes()));
	}

	@Test
	void offersNoLinkToClick() {
		assertThat(VerificationEmailEn.body("123456")).doesNotContain("http");
	}
}
