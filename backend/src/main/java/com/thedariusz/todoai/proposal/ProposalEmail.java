package com.thedariusz.todoai.proposal;

import org.apache.commons.lang3.StringUtils;

/**
 * What the natural rhythm actually says when it comes back (S-05, FR-018): the sentence the engine
 * already phrased, and a link to the app where the four answers live.
 *
 * <p><b>It adds no prose of its own around the message, on purpose.</b> The message is the product —
 * phrased by Sonnet against this user's memory, or by {@link ProposalTemplate} when the model was
 * unreachable — and a wrapper of newsletter copy ("Cześć! Mamy dla Ciebie…") would turn a friend
 * noticing something into a mailing. The whole body is one sentence and one link.
 *
 * <p><b>Second locale-bound surface on the backend, and it is one for the same reason
 * {@link ProposalTemplate} is</b>: unlike {@code ProposalPrompt}, whose instructions merely
 * <em>name</em> the answer's language, what this class returns <em>is</em> what the user reads. A
 * second locale is a second implementation of these two methods.
 *
 * <p>No answer links in the email, and none planned here: answering happens in the app, logged in,
 * which is what stops a forwarded message from being an unauthenticated write.
 */
final class ProposalEmail {

	/**
	 * How much of the entry the subject line quotes. Content is a 500-char column and a mail client
	 * shows perhaps sixty, so the choice is between abbreviating here and letting the reader's inbox
	 * do it worse.
	 */
	private static final int SUBJECT_ENTRY_LIMIT = 60;

	private ProposalEmail() {
	}

	/**
	 * Names the entry rather than the app, so the inbox row is readable without opening it — and so
	 * two proposals about different things do not collapse into one thread. The colon is doing real
	 * work: it lets the entry stay in the form the user typed it, which any Polish preposition here
	 * would have to inflect.
	 */
	static String subject(ProposalResponse proposal) {
		return "Wróćmy do tego: "
				+ StringUtils.abbreviate(proposal.entry().content(), SUBJECT_ENTRY_LIMIT);
	}

	/** The phrased proposal, then the way back into the app. */
	static String body(ProposalResponse proposal, String baseUrl) {
		return """
				%s

				Odpowiedz w aplikacji: %s/goals
				""".formatted(proposal.message(), StringUtils.removeEnd(baseUrl, "/"));
	}
}
