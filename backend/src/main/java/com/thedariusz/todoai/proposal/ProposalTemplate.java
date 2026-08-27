package com.thedariusz.todoai.proposal;

import java.time.Month;

import com.thedariusz.todoai.goal.Goal;

/**
 * The proposal the app falls back to when the model does not answer — the {@code LlmException} catch
 * arm of {@link ProposalService}, and the roadmap's 08.09 gate. Built now rather than held in
 * reserve: it is the same code either way, and having it means the whole loop is exercisable with
 * the network unplugged.
 *
 * <p>Deterministic and model-free, which is what lets {@code ProposalTemplateTest} assert this arm
 * exactly rather than merely asserting that <em>something</em> came back.
 *
 * <p><b>The one locale-bound surface on the backend</b> — the sentence, the month names and the
 * plural rule below are all one language's, and they have to be: unlike {@link ProposalPrompt},
 * whose instructions merely <em>name</em> the answer's language, what this class returns <em>is</em>
 * what the user reads. A second locale is a second implementation of {@link #phrase}, chosen by the
 * caller's locale; nothing else in the package changes.
 */
final class ProposalTemplate {

	/**
	 * Spelled out rather than taken from {@code Month#getDisplayName}, which returns the nominative
	 * for this locale where the sentence needs the locative — and which of the two a JDK yields has
	 * moved between CLDR versions. Twelve literals are boring; a sentence with the wrong case is not.
	 */
	private static final String[] IN_MONTH = {"styczniu", "lutym", "marcu", "kwietniu", "maju",
			"czerwcu", "lipcu", "sierpniu", "wrześniu", "październiku", "listopadzie", "grudniu"};

	private ProposalTemplate() {
	}

	static String phrase(Goal entry, long neglectedDays) {
		return "W %s wpisałeś: „%s” — %s. Wracamy do tego?"
				.formatted(inMonth(entry.getCreatedAt().getMonth()), entry.getContent(), elapsed(neglectedDays));
	}

	private static String inMonth(Month month) {
		return IN_MONTH[month.getValue() - 1];
	}

	/**
	 * Zero days is a real answer, not a bug: an overdue task edited today is neglected on its term
	 * rather than on its silence, so it gets the sentence that is actually true about it. Above two
	 * months the count switches to months, because a three-digit day count is a number nobody feels.
	 *
	 * <p>The noun {@code dni} needs no table past the count of one, but <b>the verb still inflects</b>
	 * — "minęły 2 dni" against "minęło 5 dni" — so it goes through the same three-form rule the
	 * months already use. A count is the one thing in this sentence that is never the same twice.
	 */
	private static String elapsed(long days) {
		if (days == 0) {
			return "termin już minął";
		}
		if (days < 60) {
			return days == 1 ? "minął dzień" : elapsed(days, "dni");
		}
		long months = days / 30;
		return elapsed(months, plural(months, "miesiąc", "miesiące", "miesięcy"));
	}

	/** The verb agrees with the count exactly as the noun does, by the same rule. */
	private static String elapsed(long count, String unit) {
		return plural(count, "minął", "minęły", "minęło") + " " + count + " " + unit;
	}

	/** This locale's three-form rule: one, the 2–4 group (12–14 excepted), everything else. */
	private static String plural(long count, String one, String few, String many) {
		if (count == 1) {
			return one;
		}
		long lastTwo = count % 100;
		long last = count % 10;
		return (last >= 2 && last <= 4 && (lastTwo < 12 || lastTwo > 14)) ? few : many;
	}
}
