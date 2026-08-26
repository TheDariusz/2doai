package com.thedariusz.todoai.proposal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.UUID;

import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalHorizon;
import com.thedariusz.todoai.goal.GoalLayer;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The sentence a demo falls back to when the model is unreachable (the roadmap's 08.09 gate), so it
 * has to read correctly without anyone proofreading it first. This is the one place the current
 * locale's wording is pinned — the month table, the day/month switch and the three-form plural are
 * branches an end-to-end test cannot reach, while {@code ProposalApiTest} covers that the arm is
 * wired at all. A second locale gets its own copy of this class, not extra cases here.
 */
class ProposalTemplateTest {

	/** {@code created_at} is a {@code @CreationTimestamp}, so a unit test has to place it by hand. */
	private static Goal createdIn(int year, int month, String content) {
		Goal goal = new Goal(UUID.randomUUID(), content, GoalLayer.GOAL, GoalHorizon.THIS_YEAR, null,
				LifeDomain.CAREER);
		ReflectionTestUtils.setField(goal, "createdAt",
				OffsetDateTime.of(year, month, 12, 9, 0, 0, 0, ZoneOffset.UTC));
		return goal;
	}

	@Test
	void quotesTheEntryAndTheMonthItWasWrittenIn() {
		assertThat(ProposalTemplate.phrase(createdIn(2026, 1, "Zrobić prawo jazdy"), 240))
				.isEqualTo("W styczniu wpisałeś: „Zrobić prawo jazdy” — minęło 8 miesięcy. Wracamy do tego?");
	}

	@Test
	void countsInDaysUntilTwoMonthsAndInMonthsAfterThat() {
		assertThat(ProposalTemplate.phrase(createdIn(2026, 3, "x"), 59)).contains("minęło 59 dni");
		assertThat(ProposalTemplate.phrase(createdIn(2026, 3, "x"), 60)).contains("minęło 2 miesiące");
	}

	@Test
	void inflectsTheCountTheWayThisLocaleDoes() {
		assertThat(ProposalTemplate.phrase(createdIn(2026, 5, "x"), 33 * 30)).contains("33 miesiące");
		// The 12–14 exception: thirteen takes the many-form even though it ends in three.
		assertThat(ProposalTemplate.phrase(createdIn(2026, 5, "x"), 13 * 30)).contains("13 miesięcy");
		assertThat(ProposalTemplate.phrase(createdIn(2026, 5, "x"), 1)).contains("minął dzień");
	}

	@Test
	void saysTheTermPassedRatherThanCountingZeroDaysOfSilence() {
		// An overdue task edited today: neglected on its term, not on its silence.
		Goal overdue = new Goal(UUID.randomUUID(), "Oddać książkę", GoalLayer.TASK, null,
				LocalDate.of(2026, 8, 1), LifeDomain.EDUCATION);
		ReflectionTestUtils.setField(overdue, "createdAt",
				OffsetDateTime.of(2026, 7, 2, 9, 0, 0, 0, ZoneOffset.UTC));

		assertThat(ProposalTemplate.phrase(overdue, 0))
				.isEqualTo("W lipcu wpisałeś: „Oddać książkę” — termin już minął. Wracamy do tego?");
	}
}
