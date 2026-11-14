package com.thedariusz.todoai.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The link in the code mail. Address and code ride in the fragment, which browsers never send to a
 * server and never put in a {@code Referer}, so neither reaches a log line on the way in.
 */
class SignInLinkTest {

	@Test
	void pointsAtTheVerifyScreenWithTheAddressAndCodeInTheFragment() {
		assertThat(SignInLink.of("https://2doai.app", "ala@example.pl", "04231789"))
				.isEqualTo("https://2doai.app/verify#email=ala%40example.pl&code=04231789");
	}

	/** A {@code +} left raw would be read back as a space, and the prefilled address would be wrong. */
	@Test
	void encodesAnAddressCarryingAPlus() {
		assertThat(SignInLink.of("https://2doai.app", "ala+todo@example.pl", "04231789"))
				.contains("email=ala%2Btodo%40example.pl&");
	}

	@Test
	void toleratesATrailingSlashOnTheBaseUrl() {
		assertThat(SignInLink.of("http://localhost:5173/", "ala@example.pl", "04231789"))
				.startsWith("http://localhost:5173/verify#");
	}
}
