package com.thedariusz.todoai.proposal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalLayer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static com.thedariusz.todoai.proposal.ProposalTemplateTest.createdIn;
import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code ProposalTemplateTest}'s sibling, for the sibling implementation — the same four things
 * pinned about the other locale's sentence, because the fallback is what the user reads when the
 * model is unreachable and nobody proofreads it first.
 *
 * <p>Deliberately a copy of that class's cases rather than a parameterized rewrite of it: the two
 * sentences agree on <em>what</em> they say and on nothing about how, and a shared harness would
 * have to be written in whichever grammar has more rules.
 */
class ProposalTemplateEnTest {

	@Test
	void quotesTheEntryAndTheMonthItWasWrittenIn() {
		assertThat(ProposalTemplateEn.phrase(createdIn(2026, 1, "Get a driving licence"), 240))
				.isEqualTo("In January you wrote: “Get a driving licence” — 8 months have passed. "
						+ "Shall we come back to it?");
	}

	@Test
	void countsInDaysUntilTwoMonthsAndInMonthsAfterThat() {
		assertThat(ProposalTemplateEn.phrase(createdIn(2026, 3, "x"), 59)).contains("59 days have passed");
		assertThat(ProposalTemplateEn.phrase(createdIn(2026, 3, "x"), 60)).contains("2 months have passed");
	}

	/** The whole of this locale's plural rule, and the whole of its verb agreement with it. */
	@Test
	void agreesWithTheCountTheWayThisLocaleDoes() {
		assertThat(ProposalTemplateEn.phrase(createdIn(2026, 5, "x"), 1)).contains("1 day has passed");
		assertThat(ProposalTemplateEn.phrase(createdIn(2026, 5, "x"), 2)).contains("2 days have passed");
		assertThat(ProposalTemplateEn.phrase(createdIn(2026, 5, "x"), 30)).contains("30 days have passed");
		assertThat(ProposalTemplateEn.phrase(createdIn(2026, 5, "x"), 33 * 30))
				.contains("33 months have passed");
	}

	@Test
	void saysTheTermPassedRatherThanCountingZeroDaysOfSilence() {
		// An overdue task edited today: neglected on its term, not on its silence.
		Goal overdue = new Goal(UUID.randomUUID(), "Return the book", GoalLayer.TASK, null,
				LocalDate.of(2026, 8, 1), LifeDomain.EDUCATION);
		ReflectionTestUtils.setField(overdue, "createdAt",
				OffsetDateTime.of(2026, 7, 2, 9, 0, 0, 0, ZoneOffset.UTC));

		assertThat(ProposalTemplateEn.phrase(overdue, 0))
				.isEqualTo("In July you wrote: “Return the book” — its deadline has passed. "
						+ "Shall we come back to it?");
	}
}
