package com.thedariusz.todoai.proposal;

import java.util.UUID;

import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalLayer;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * What the rhythm says when it arrives in an inbox. Two things have to be true of it and nothing
 * else: the phrased proposal survives intact, and there is a way back into the app — that <em>is</em>
 * FR-018's email.
 */
class ProposalEmailTest {

	private static final String PHRASED = "W czerwcu wpisałeś: „Oddać książkę” — minęło 40 dni. Wracamy do tego?";

	@Test
	void carriesThePhrasedProposalAndTheWayBackIntoTheApp() {
		String body = ProposalEmail.body(proposal("Oddać książkę"), "https://2doai.app");

		assertThat(body).contains(PHRASED).contains("https://2doai.app/goals");
	}

	/**
	 * A base URL is configuration, and configuration acquires stray slashes. Either spelling has to
	 * produce a link that opens rather than a 404 on {@code //goals}.
	 */
	@Test
	void survivesABaseUrlWithATrailingSlash() {
		String body = ProposalEmail.body(proposal("Oddać książkę"), "https://2doai.app/");

		assertThat(body).contains("https://2doai.app/goals").doesNotContain("//goals");
	}

	/**
	 * The subject names the entry, not the app: an inbox row has to be readable without being opened,
	 * and two proposals about different things must not collapse into one thread.
	 */
	@Test
	void namesTheEntryInTheSubject() {
		assertThat(ProposalEmail.subject(proposal("Oddać książkę"))).contains("Oddać książkę");
	}

	/**
	 * {@code goal.content} is a 500-char column and a subject line is not. Abbreviating here is the
	 * choice against letting the reader's mail client truncate it worse.
	 */
	@Test
	void abbreviatesAnEntryFarTooLongToBeASubjectLine() {
		String subject = ProposalEmail.subject(proposal("Zrobić " + "bardzo ".repeat(80) + "dużo"));

		assertThat(subject).hasSizeLessThan(100).endsWith("...");
	}

	private static ProposalResponse proposal(String entry) {
		UUID account = UUID.randomUUID();
		Goal goal = new Goal(account, entry, GoalLayer.TASK, null, null, LifeDomain.EDUCATION);
		return ProposalResponse.of(
				new Proposal(account, UUID.randomUUID(), PHRASED, 40, Proposal.Source.LLM), goal, null);
	}
}
