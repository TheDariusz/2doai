package com.thedariusz.todoai.proposal;

import java.time.format.TextStyle;

import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.user.AppLanguage;

/**
 * {@link ProposalTemplate}'s sibling for the other language — the second implementation of
 * {@code phrase} that class's javadoc calls for, rather than branches inside the first one. What the
 * two share is the shape of the sentence and the three readings of the count; they share no wording,
 * because every word here is what a user reads.
 *
 * <p><b>Markedly smaller, and that asymmetry is the point.</b> This locale inflects neither the
 * month nor the noun by case, so the month comes straight from the JDK and the plural rule is one
 * comparison — where the sibling needs twelve literals and a three-form table. Trying to serve both
 * from one implementation would mean writing the simpler language in the harder one's machinery.
 */
final class ProposalTemplateEn {

	private ProposalTemplateEn() {
	}

	static String phrase(Goal entry, long neglectedDays) {
		return "In %s you wrote: “%s” — %s. Shall we come back to it?".formatted(
				entry.getCreatedAt().getMonth().getDisplayName(TextStyle.FULL, AppLanguage.EN.locale()),
				entry.getContent(), elapsed(neglectedDays));
	}

	/**
	 * The same three readings of the count the sibling makes, and they have to stay the same three:
	 * zero means the entry was picked on its passed term rather than on its silence, and above two
	 * months a day count is a number nobody feels.
	 */
	private static String elapsed(long days) {
		if (days == 0) {
			return "its deadline has passed";
		}
		return days < 60 ? passed(days, "day") : passed(days / 30, "month");
	}

	/** This locale's entire plural rule, verb agreement included. */
	private static String passed(long count, String unit) {
		return count == 1 ? "1 " + unit + " has passed" : count + " " + unit + "s have passed";
	}
}
