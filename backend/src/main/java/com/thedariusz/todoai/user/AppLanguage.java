package com.thedariusz.todoai.user;

import java.util.Locale;

/**
 * The languages 2do AI speaks to a person in (DEV-49). A two-valued domain type rather than a bare
 * string, so no layer has to re-derive what a locale tag means, and so the "unrecognised means
 * English" rule lives in exactly one place.
 *
 * <p>Persisted by {@link #name()} in {@code app_user.preferred_language VARCHAR(2)} and carried on
 * the wire as the same two letters — not a BCP 47 tag. There are two locales and no region variants,
 * and a client that had to choose between {@code en} and {@code en-GB} would be choosing about
 * nothing.
 *
 * <p><b>Two different fallbacks live in this change and they are not the same rule.</b>
 * {@link #DEFAULT} is what a <em>request</em> naming no language the app speaks resolves to
 * (English — the app is English-first). An <em>account</em> that never chose reads as Polish, which
 * is {@link User#getLanguage()}'s rule and belongs there: it is about accounts that predate the
 * choice being offered (FR-004), not about what an unknown locale means.
 */
public enum AppLanguage {

	PL(Locale.of("pl"), "Polish"),
	EN(Locale.ENGLISH, "English");

	/** What a request resolves to when it names no language the app speaks, or names none at all. */
	public static final AppLanguage DEFAULT = EN;

	private final Locale locale;

	private final String englishName;

	AppLanguage(Locale locale, String englishName) {
		this.locale = locale;
		this.englishName = englishName;
	}

	/**
	 * Reads a resolved {@link Locale} as one of the two. Region is ignored: {@code en-GB} and
	 * {@code en-US} are the same language to this app, and pretending otherwise would mean a second
	 * catalog for no second reader.
	 *
	 * @param locale the locale to read, or {@code null}
	 * @return the matching language, or {@link #DEFAULT} for {@code null} and anything else
	 */
	public static AppLanguage of(Locale locale) {
		if (locale == null) {
			return DEFAULT;
		}
		for (AppLanguage language : values()) {
			if (language.locale.getLanguage().equals(locale.getLanguage())) {
				return language;
			}
		}
		return DEFAULT;
	}

	/** The locale this language negotiates as, for Spring's {@code Accept-Language} resolution. */
	public Locale locale() {
		return locale;
	}

	/**
	 * This language's name <em>in English</em> — the word the model is told to answer in.
	 *
	 * <p>The prompts stay English and <em>name</em> their output language rather than demonstrating
	 * it by being written in it (CLAUDE.md: "a Polish prompt is the bug, even when the answer must be
	 * Polish"), which is what keeps a second locale one interpolated word instead of a second copy of
	 * every instruction. Consumed by {@code ProposalPrompt}.
	 */
	public String englishName() {
		return englishName;
	}
}
