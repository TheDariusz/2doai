package com.thedariusz.todoai.proposal;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The proposal aggregate's one invariant, asserted without a container: a proposal is answered
 * <em>once</em>, and the answer and the moment it arrived can never disagree — which is why
 * {@link Proposal#answer} exists instead of two setters.
 *
 * <p>The FR-018 "at most one pending" rule is <b>not</b> here: it spans rows, so it belongs to the
 * partial unique index and to {@code ProposalPersistenceTest}. This class only owns what one row can
 * decide on its own.
 */
class ProposalTest {

	private static final OffsetDateTime NOW = OffsetDateTime.parse("2026-08-25T20:00:00Z");

	@Test
	void staysPendingUntilItIsAnswered() {
		Proposal proposal = proposal();

		assertThat(proposal.isPending()).isTrue();
		assertThat(proposal.getAnswer()).isNull();

		proposal.answer(ProposalAnswer.NOT_NOW, NOW);

		assertThat(proposal.isPending())
				.as("pending is read off answered_at, which is what the partial unique index indexes")
				.isFalse();
		assertThat(proposal.getAnswer()).isEqualTo(ProposalAnswer.NOT_NOW);
	}

	/**
	 * Truncated for the same reason {@code Goal#complete} truncates: {@code timestamptz} keeps
	 * microseconds, so a finer clock (Linux has one, macOS does not) would let the answer response
	 * carry a moment no later read of the row can return.
	 */
	@Test
	void keepsTheAnsweredMomentAtAPrecisionThePostgresColumnCanReturn() {
		Proposal proposal = proposal();

		proposal.answer(ProposalAnswer.STARTING, OffsetDateTime.parse("2026-08-25T20:00:00.123456789Z"));

		assertThat(proposal.getAnsweredAt()).isEqualTo(OffsetDateTime.parse("2026-08-25T20:00:00.123456Z"));
	}

	@Test
	void refusesToBeAnsweredTwice() {
		Proposal proposal = proposal();
		proposal.answer(ProposalAnswer.NEVER, NOW);

		assertThatThrownBy(() -> proposal.answer(ProposalAnswer.STARTING, NOW.plusMinutes(1)))
				.as("a second answer would silently overwrite the first — the API owes the caller a "
						+ "409 instead, and it can only tell because this throws")
				.isInstanceOf(IllegalStateException.class);

		assertThat(proposal.getAnswer()).isEqualTo(ProposalAnswer.NEVER);
	}

	@Test
	void carriesTheGeneratedFirstStepWhenThereIsOne() {
		Proposal proposal = proposal();
		assertThat(proposal.getFirstStep()).isNull();

		proposal.recordFirstStep("{\"steps\":[\"Wyjmij rower z piwnicy\"]}");

		assertThat(proposal.getFirstStep()).contains("Wyjmij rower z piwnicy");
	}

	private Proposal proposal() {
		return new Proposal(UUID.randomUUID(), UUID.randomUUID(),
				"Osiem miesięcy temu wpisałeś „Wrócić na rower” — chcesz do tego wrócić?", 240,
				Proposal.Source.LLM);
	}
}
