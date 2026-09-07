package com.thedariusz.todoai.user;

import java.util.Locale;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The two-valued language type and the one place the "unrecognised means English" rule lives, so no
 * layer has to re-derive it from a bare string.
 *
 * <p>Note the two <em>different</em> fallbacks this change deliberately carries, which is why they
 * are asserted here side by side. A <b>request</b> that names no language the app speaks resolves to
 * {@link AppLanguage#DEFAULT} (English — the app is English-first). An <b>account</b> that never
 * chose one reads as Polish, because it predates the choice ever being offered (FR-004); that rule
 * belongs to {@link User#getLanguage()} and is asserted there.
 */
class AppLanguageTest {

	@Test
	void readsTheLanguageOfALocaleAndIgnoresItsRegion() {
		assertThat(AppLanguage.of(Locale.of("pl", "PL"))).isEqualTo(AppLanguage.PL);
		assertThat(AppLanguage.of(Locale.of("en", "GB"))).isEqualTo(AppLanguage.EN);
		assertThat(AppLanguage.of(Locale.ENGLISH)).isEqualTo(AppLanguage.EN);
	}

	@Test
	void fallsBackToEnglishForALocaleTheAppDoesNotSpeak() {
		assertThat(AppLanguage.of(Locale.GERMAN)).isEqualTo(AppLanguage.EN);
		assertThat(AppLanguage.of(Locale.of("cs"))).isEqualTo(AppLanguage.EN);
	}

	@Test
	void fallsBackToEnglishForNoLocaleAtAll() {
		assertThat(AppLanguage.of(null)).isEqualTo(AppLanguage.EN);
		assertThat(AppLanguage.DEFAULT).isEqualTo(AppLanguage.EN);
	}

	/**
	 * The word Phase 3 interpolates into the prompt so the model is <em>told</em> which language to
	 * answer in, rather than the prompt demonstrating it by being written in that language
	 * (CLAUDE.md: "a Polish prompt is the bug, even when the answer must be Polish").
	 */
	@Test
	void namesItselfInEnglishForThePrompt() {
		assertThat(AppLanguage.PL.englishName()).isEqualTo("Polish");
		assertThat(AppLanguage.EN.englishName()).isEqualTo("English");
	}

	/** {@code name()} is what the {@code app_user.preferred_language VARCHAR(2)} column stores. */
	@Test
	void persistsAsATwoCharacterName() {
		for (AppLanguage language : AppLanguage.values()) {
			assertThat(language.name()).hasSize(2);
		}
	}
}
