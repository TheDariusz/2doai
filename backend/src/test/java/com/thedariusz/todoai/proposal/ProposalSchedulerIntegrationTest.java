package com.thedariusz.todoai.proposal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.ai.LlmClient;
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
 * One whole cycle of the natural rhythm against a real Postgres: the moment is drawn, stored, read
 * back after a "restart", fired, and drawn again. {@code ProposalSchedulerTest} states the rules
 * against mocks; this states that the column, the map and the service actually meet.
 *
 * <p>The boot load is driven by calling {@link ProposalScheduler#loadSchedule()} rather than by
 * booting a context — a {@code @SpringBootTest} context comes up before its test can insert anything,
 * so the only account a real {@code ApplicationReadyEvent} could ever see here is none. The event
 * wiring itself is one annotation, and the startup log line is the manual check that it fired.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ProposalSchedulerIntegrationTest {

	private static final String PHRASED = "Zauważyłem, że ta rzecz leży odłogiem. Wracamy do niej?";

	@MockitoBean
	private LlmClient llm;

	@Autowired
	private ProposalScheduler scheduler;

	@Autowired
	private UserRepository users;

	@Autowired
	private GoalRepository goals;

	@Autowired
	private ProposalRepository proposalRows;

	@BeforeEach
	void phraseEveryProposal() {
		when(llm.complete(any())).thenReturn(PHRASED);
	}

	@Test
	void backfillsAnAccountThatHasNeverBeenScheduled() {
		UUID account = accountWithAnOverdueEntry("Oddać książkę");

		scheduler.loadSchedule();

		OffsetDateTime next = users.findById(account).orElseThrow().getNextProposalAt();
		assertThat(ChronoUnit.DAYS.between(LocalDate.now(ProposalRhythm.USER_ZONE),
				next.atZoneSameInstant(ProposalRhythm.USER_ZONE).toLocalDate()))
				.isBetween(2L, 7L);
	}

	@Test
	void comesBackOnItsOwnWhenTheStoredMomentHasPassed() {
		UUID account = accountWithAnOverdueEntry("Oddać książkę");
		OffsetDateTime midday = today(12);
		schedule(account, midday.minusDays(1));

		scheduler.loadSchedule();
		scheduler.fireDue(midday);

		assertThat(proposalRows.findByUserIdAndAnsweredAtIsNull(account))
				.as("nobody pressed anything — the app came back on its own")
				.isPresent();
		assertThat(users.findById(account).orElseThrow().getNextProposalAt()).isAfter(midday);
	}

	/** A user with one overdue task — the only neglect signal a freshly written row can carry. */
	private UUID accountWithAnOverdueEntry(String content) {
		UUID account = users.saveAndFlush(new User(Email.of("owner-" + UUID.randomUUID() + "@example.com"),
				"{bcrypt}$2a$10$hash")).getId();
		goals.saveAndFlush(new Goal(account, content, GoalLayer.TASK, null,
				LocalDate.now(ProposalRhythm.USER_ZONE).minusDays(2), LifeDomain.EDUCATION));
		return account;
	}

	private void schedule(UUID account, OffsetDateTime at) {
		User row = users.findById(account).orElseThrow();
		row.scheduleNextProposalAt(at);
		users.saveAndFlush(row);
	}

	/** An hour of today in the user's zone, so the fire lands inside the send window whenever CI runs. */
	private static OffsetDateTime today(int hour) {
		return LocalDate.now(ProposalRhythm.USER_ZONE).atTime(hour, 0)
				.atZone(ProposalRhythm.USER_ZONE).toOffsetDateTime();
	}
}
