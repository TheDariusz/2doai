package com.thedariusz.todoai.proposal;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.ai.LlmClient;
import com.thedariusz.todoai.ai.memory.AiMemoryService;
import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalLayer;
import com.thedariusz.todoai.goal.GoalRepository;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * The path the natural rhythm runs (S-05, FR-011/FR-018): {@link ProposalService#proposeScheduled}
 * against a real Postgres, because the rule it has to get right is a schema one — the pending slot is
 * a partial unique index, so "supersede the old proposal, then open the new one" either happens in
 * that order or fails the insert.
 *
 * <p>Driven through the service rather than over HTTP, and that is the point of the method: no
 * scheduler thread ever has a {@code SecurityContext}, so there is no request to make. The endpoint
 * half of this slice — a client trying to write {@code SUPERSEDED} itself — is asserted over the wire
 * in {@code ProposalApiTest}, where the answer endpoint already lives.
 *
 * <p>Neglect is driven through {@code due_date} for the reason {@code ProposalApiTest} gives: idle
 * time is {@code @UpdateTimestamp} and no test can age a row it just wrote. The thresholds themselves
 * belong to {@code ProposalSelectorTest}.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ScheduledProposalTest {

	private static final String PHRASED = "Zauważyłem, że ta rzecz leży odłogiem. Wracamy do niej?";

	@MockitoBean
	private LlmClient llm;

	@Autowired
	private ProposalService proposals;

	@Autowired
	private ProposalRepository proposalRows;

	@Autowired
	private GoalRepository goals;

	@Autowired
	private UserRepository users;

	@Autowired
	private AiMemoryService memory;

	@BeforeEach
	void phraseEveryProposal() {
		when(llm.complete(any())).thenReturn(PHRASED);
	}

	@Test
	void supersedesTheProposalNobodyAnsweredAndComesBackWithADifferentEntry() {
		UUID user = userWithOverdueEntries("Oddać książkę", "Wymienić opony");
		UUID ignored = proposals.proposeScheduled(user).orElseThrow().id();

		ProposalResponse replacement = proposals.proposeScheduled(user).orElseThrow();

		assertThat(replacement.id()).isNotEqualTo(ignored);
		assertThat(replacement.entry().content())
				.as("the friend does not ask about the same thing twice in a row — being ignored the "
						+ "first time is exactly what that would read as")
				.isEqualTo("Wymienić opony");
		assertThat(proposalRows.findByIdAndUserId(ignored, user).orElseThrow().getAnswer())
				.isEqualTo(ProposalAnswer.SUPERSEDED);
	}

	@Test
	void quietsTheIgnoredEntryForTheSameThreeDaysASpokenNotNowBuys() {
		UUID user = userWithOverdueEntries("Oddać książkę", "Wymienić opony");
		UUID ignoredEntry = proposals.proposeScheduled(user).orElseThrow().entry().id();

		proposals.proposeScheduled(user);

		// FR-018's implicit "not now": silence is an answer, and it lands on the entry like every
		// other one does — which is also what stops the entry coming straight back next cycle.
		assertThat(goals.findByIdAndUserId(ignoredEntry, user).orElseThrow().getRemindAfter())
				.isEqualTo(LocalDate.now(ProposalRhythm.USER_ZONE).plusDays(3));
	}

	/**
	 * The ordering the whole method is arranged around. Superseding first would quiet the entry and
	 * then discover there is nothing to replace it with — leaving the user, on the one cycle the app
	 * came back on its own, with nothing at all.
	 */
	@Test
	void leavesThePendingProposalStandingWhenThereIsNothingToReplaceItWith() {
		UUID user = userWithOverdueEntries("Oddać książkę");
		UUID only = proposals.proposeScheduled(user).orElseThrow().id();

		Optional<ProposalResponse> nothing = proposals.proposeScheduled(user);

		assertThat(nothing).isEmpty();
		assertThat(proposalRows.findByUserIdAndAnsweredAtIsNull(user))
				.get()
				.extracting(Proposal::getId)
				.isEqualTo(only);
	}

	/**
	 * The memory feeds the next proposal's prompt, so "the user said not now" and "the user said
	 * nothing at all" have to reach it as different facts. Recording silence under
	 * {@code proposal_answered} would teach the model the user answers every time.
	 */
	@Test
	void recordsSilenceAsItsOwnEpisodeRatherThanAsAnAnswerTheUserGave() {
		UUID user = userWithOverdueEntries("Oddać książkę", "Wymienić opony");
		proposals.proposeScheduled(user);

		proposals.proposeScheduled(user);

		assertThat(memory.renderFor(user))
				.contains("proposal_superseded")
				.contains("Oddać książkę")
				.doesNotContain("proposal_answered");
	}

	@Test
	void neverReachesAnotherAccountsEntries() {
		UUID mine = userWithOverdueEntries("Oddać książkę");
		userWithOverdueEntries("Wymienić opony");

		assertThat(proposals.proposeScheduled(mine).orElseThrow().entry().content())
				.isEqualTo("Oddać książkę");
	}

	/** A user with one overdue task per name — the only neglect signal a freshly written row can carry. */
	private UUID userWithOverdueEntries(String... contents) {
		UUID userId = users.saveAndFlush(new User(Email.of("owner-" + UUID.randomUUID() + "@example.com"),
				"{bcrypt}$2a$10$hash")).getId();

		// Distinct domains so the balancing rule has nothing to say, and created in order so the
		// comparator's final tie-break (UUID v7 ascends with creation) picks the first one named.
		List<LifeDomain> domains = List.of(LifeDomain.EDUCATION, LifeDomain.TRANSPORT);
		for (int at = 0; at < contents.length; at++) {
			goals.saveAndFlush(new Goal(userId, contents[at], GoalLayer.TASK, null,
					LocalDate.now(ProposalRhythm.USER_ZONE).minusDays(2), domains.get(at)));
		}
		return userId;
	}
}
