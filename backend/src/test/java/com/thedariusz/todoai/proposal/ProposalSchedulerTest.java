package com.thedariusz.todoai.proposal;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalLayer;
import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.mail.MailDeliveryException;
import com.thedariusz.todoai.mail.MailboxProperties;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRegistered;
import com.thedariusz.todoai.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.boot.health.contributor.Status;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The loop that makes the app come back on its own (S-05, FR-011), asserted without a container: the
 * schedule is a {@code Map} and the tick takes its moment as an argument, so every rule here is a
 * plain call.
 *
 * <p><b>The load-bearing assertion is {@link #touchesNothingAtAllWhileNobodyIsDue()}.</b> Everything
 * else in this class describes the product; that one describes the bill. Neon suspends its compute
 * after ~5 minutes without a query, so a tick that read <em>anything</em> — even a cheap count —
 * would hold the database awake permanently for the roughly one fire per 2-7 days it exists to
 * perform (see {@code context/foundation/lessons.md}). It is the reason the schedule lives in memory
 * at all, and a mock is the only way to say "no query happened" out loud.
 */
class ProposalSchedulerTest {

	private static final RhythmProperties RHYTHM = new RhythmProperties(2, 7, 9, 21);

	/** Inside the send window, in the user's zone — the ordinary tick. */
	private static final OffsetDateTime MIDDAY = warsaw(12);

	/** Long before it: what a machine that was down over the whole window comes back to. */
	private static final OffsetDateTime DAWN = warsaw(4);

	private static final MailboxProperties MAILBOX =
			new MailboxProperties("2do AI <propozycje@2doai.app>", "https://2doai.app");

	private final UserRepository users = mock(UserRepository.class);

	private final ProposalService proposals = mock(ProposalService.class);

	private final EmailSender mail = mock(EmailSender.class);

	private final ProposalScheduler scheduler =
			new ProposalScheduler(users, proposals, RHYTHM, mail, MAILBOX);

	@Test
	void comesBackToAnAccountWhoseMomentHasArrived() {
		User account = loaded(MIDDAY.minusHours(1));

		scheduler.fireDue(MIDDAY);

		verify(proposals).proposeScheduled(account.getId());
	}

	/**
	 * The window is not decoration. A drawn moment always lands inside it, but a moment that went by
	 * while the machine was down does not — and a friend who was quiet for a day does not make up for
	 * it at 04:00.
	 */
	@Test
	void waitsForTheUsersWakingHoursRatherThanFiringTheMomentItIsLate() {
		loaded(DAWN.minusDays(1));

		scheduler.fireDue(DAWN);

		verifyNoInteractions(proposals);
	}

	@Test
	void touchesNothingAtAllWhileNobodyIsDue() {
		loaded(MIDDAY.plusDays(3));

		scheduler.fireDue(MIDDAY);

		verifyNoInteractions(users, proposals, mail);
	}

	/**
	 * FR-018's first channel, and the reason this loop is worth having: a user who is not looking at
	 * the app cannot be told anything by it. The address comes off the row the fire already loaded.
	 */
	@Test
	void emailsThePhrasedProposalToTheAccountItWasDrawnFor() {
		User account = loaded(MIDDAY.minusHours(1));
		when(proposals.proposeScheduled(account.getId())).thenReturn(Optional.of(proposal()));

		scheduler.fireDue(MIDDAY);

		verify(mail).send(eq(account.getEmail()), contains("Oddać książkę"),
				contains("https://2doai.app/goals"));
	}

	/** Nothing neglected is not an occasion to write to somebody. */
	@Test
	void staysQuietWhenThereWasNothingToPropose() {
		User account = loaded(MIDDAY.minusHours(1));
		when(proposals.proposeScheduled(account.getId())).thenReturn(Optional.empty());

		scheduler.fireDue(MIDDAY);

		verifyNoInteractions(mail);
	}

	/**
	 * The failure plan, and the reason the proposal is written before the email is attempted: a
	 * message that will not send costs the user the nudge, never the proposal. It is already pending
	 * and the card is waiting the next time they open the app — which is also why there is no retry
	 * queue. What must not happen is the rhythm stopping, so the cycle still moves on.
	 */
	@Test
	void leavesTheProposalWaitingInTheAppWhenTheEmailCannotBeDelivered() {
		User account = loaded(MIDDAY.minusHours(1));
		when(proposals.proposeScheduled(account.getId())).thenReturn(Optional.of(proposal()));
		doThrow(new MailDeliveryException("530 authentication required", new IllegalStateException()))
				.when(mail).send(anyString(), anyString(), anyString());

		scheduler.fireDue(MIDDAY);

		assertThat(drawnFor(account)).isAfter(MIDDAY);
	}

	@Test
	void drawsTheNextMomentInsideTheConfiguredIntervalAfterEveryFire() {
		User account = loaded(MIDDAY.minusHours(1));

		scheduler.fireDue(MIDDAY);

		assertThat(daysFromMiddayTo(drawnFor(account))).isBetween(2L, 7L);
	}

	/**
	 * Nothing neglected is a perfectly ordinary answer, and the rhythm has to survive it. Leaving the
	 * moment where it was would make the user due again on the next tick — a database query every 60
	 * seconds, which is exactly what the in-memory schedule exists to prevent.
	 */
	@Test
	void movesTheRhythmOnEvenWhenThereWasNothingToPropose() {
		User account = loaded(MIDDAY.minusHours(1));
		when(proposals.proposeScheduled(account.getId())).thenReturn(Optional.empty());

		scheduler.fireDue(MIDDAY);

		assertThat(drawnFor(account)).isAfter(MIDDAY);
	}

	/** Same reasoning: one account's failure must not become a 60-second retry loop against Neon. */
	@Test
	void movesTheRhythmOnEvenWhenTheFireItselfFailed() {
		User account = loaded(MIDDAY.minusHours(1));
		when(proposals.proposeScheduled(account.getId())).thenThrow(new IllegalStateException("boom"));

		scheduler.fireDue(MIDDAY);

		assertThat(drawnFor(account)).isAfter(MIDDAY);
	}

	/**
	 * The redraw is the one step of a fire that has no failure it may skip for, so it is the one step
	 * whose own failure has to be survivable too. A Neon blip is exactly the transient this design is
	 * built around, and a map left holding a moment already in the past is not one lost proposal — it
	 * is a fire, a Sonnet call and an email <em>every 60 seconds</em> until the database comes back,
	 * against an account that has already been written to and emailed once.
	 */
	@Test
	void keepsTheRhythmMovingWhenTheNextMomentCannotBeStored() {
		User account = loaded(MIDDAY.minusHours(1));
		when(users.scheduleNextProposalAt(eq(account.getId()), any(), any()))
				.thenThrow(new DataAccessResourceFailureException("neon is asleep"));

		scheduler.fireDue(MIDDAY);
		clearInvocations(proposals, mail);
		scheduler.fireDue(MIDDAY);

		verifyNoInteractions(proposals, mail);
	}

	/**
	 * {@code ConcurrentHashMap.forEach} stops at the first throw, so a fire that lets one escape does
	 * not cost one account its turn — it costs every account the iteration had not reached yet, on
	 * this tick and on every tick after it.
	 */
	@Test
	void doesNotLetOneAccountsFailureStarveTheRest() {
		User unlucky = account(MIDDAY.minusHours(1));
		User other = account(MIDDAY.minusHours(1));
		when(users.findAll()).thenReturn(List.of(unlucky, other));
		when(users.findById(unlucky.getId())).thenReturn(Optional.of(unlucky));
		when(users.findById(other.getId())).thenReturn(Optional.of(other));
		when(users.scheduleNextProposalAt(eq(unlucky.getId()), any(), any()))
				.thenThrow(new DataAccessResourceFailureException("neon is asleep"));
		scheduler.loadSchedule();

		scheduler.fireDue(MIDDAY);

		// Asserted for both rather than "the one after", because the map's iteration order is its own
		// business — the claim is that neither account depends on the other's luck.
		verify(proposals).proposeScheduled(unlucky.getId());
		verify(proposals).proposeScheduled(other.getId());
	}

	/**
	 * FR-019 deletion prunes the map at its own seam, so this covers the row that went away some other
	 * way — a rolled-back registration. Noticing it must also be the last time: an entry left behind
	 * would be due forever, and "due forever" is one query per tick.
	 */
	@Test
	void forgetsAnAccountThatNoLongerExists() {
		User account = loaded(MIDDAY.minusHours(1));
		when(users.findById(account.getId())).thenReturn(Optional.empty());
		// What the update reports for a row that is not there, and how the map learns to drop it.
		when(users.scheduleNextProposalAt(eq(account.getId()), any(), any())).thenReturn(0);

		scheduler.fireDue(MIDDAY);
		clearInvocations(users);
		scheduler.fireDue(MIDDAY);

		verifyNoInteractions(users, proposals, mail);
	}

	/**
	 * FR-019 erasure, racing its own fire. The account is loaded before a model call that can take a
	 * minute and an SMTP send that can take thirty seconds, so a deletion can and will land while a
	 * fire is in flight — and the row the fire is holding is <b>detached</b>. Writing it back with a
	 * {@code save} would merge a row that is gone, which Hibernate resolves as an INSERT: the email
	 * and password hash of an account the user asked to erase, restored by the reminder scheduler.
	 * A targeted update cannot re-create anything, so the race has no bad outcome to reach.
	 */
	@Test
	void movesTheRhythmOnByUpdateSoADeletedAccountCanNeverBeMergedBack() {
		User account = loaded(MIDDAY.minusHours(1));

		scheduler.fireDue(MIDDAY);

		verify(users).scheduleNextProposalAt(eq(account.getId()), any(), any());
		verify(users, never()).save(any());
		verify(users, never()).saveAndFlush(any());
	}

	/**
	 * The other end of the account lifecycle, and the reason the fire's ghost branch is only a safety
	 * net: a deleted account leaves the map at deletion time, so it never costs the tick the
	 * Neon-waking {@code findById} that noticing it there would.
	 */
	@Test
	void forgetsADeletedAccountAtTheDeletionSeam() {
		User account = loaded(MIDDAY.minusHours(1));

		scheduler.deleteAllForUser(account.getId());
		scheduler.fireDue(MIDDAY);

		verifyNoInteractions(users, proposals, mail);
	}

	@Test
	void schedulesAFreshAccountWithoutWaitingForARestart() {
		User account = account(null);

		scheduler.scheduleNewAccount(new UserRegistered(account.getId()));

		assertThat(drawnFor(account)).isNotNull();
	}

	@Test
	void drawsAFirstMomentAtBootForAnAccountThatHasNeverHadOne() {
		User account = account(null);
		when(users.findAll()).thenReturn(List.of(account));

		scheduler.loadSchedule();

		assertThat(drawnFor(account)).isNotNull();
	}

	/**
	 * The column exists so a restart resumes the rhythm instead of restarting it. Redrawing at boot
	 * would bunch every account's next moment around deploys and could push a proposal the user was
	 * owed this afternoon a week out — the rhythm would be a function of how often we deploy.
	 */
	@Test
	void resumesAStoredMomentAcrossARestartRatherThanRedrawingIt() {
		OffsetDateTime stored = MIDDAY.plusDays(1);
		User account = account(stored);
		when(users.findAll()).thenReturn(List.of(account));

		scheduler.loadSchedule();

		verify(users, never()).scheduleNextProposalAt(any(), any(), any());
		assertThat(account.getNextProposalAt()).isEqualTo(stored);
	}

	@Test
	void reportsUpWhileTheTickIsStillRunning() {
		scheduler.tick();

		assertThat(scheduler.health().getStatus()).isEqualTo(Status.UP);
	}

	/**
	 * The probe Fly actually calls (see {@code fly.toml}). An indicator that could only ever answer UP
	 * would be worse than none: the failure it exists to catch — the scheduler thread dying while the
	 * web server keeps answering — is invisible from every other angle, because the symptom is the app
	 * doing nothing.
	 */
	@Test
	void reportsDownOnceTheTickHasStopped() {
		ReflectionTestUtils.setField(scheduler, "lastTick", Instant.now().minus(Duration.ofHours(1)));

		assertThat(scheduler.health().getStatus()).isEqualTo(Status.DOWN);
	}

	/**
	 * Same rule at the other end of the life cycle, and the reason it earns its own test: a
	 * {@code @Scheduled} method that never got wired leaves a bean that looks perfectly healthy while
	 * the loop it reports on has never run once. Starting DOWN is what makes {@code HealthProbeApiTest}
	 * asserting UP over the wire a statement about the shipped wiring rather than about this field.
	 */
	@Test
	void reportsDownUntilTheFirstTickHasEvenHappened() {
		assertThat(scheduler.health().getStatus()).isEqualTo(Status.DOWN);
	}

	/** What a fire that found something neglected hands back — the thing the email is made of. */
	private static ProposalResponse proposal() {
		UUID owner = UUID.randomUUID();
		Goal entry = new Goal(owner, "Oddać książkę", GoalLayer.TASK, null, null, LifeDomain.EDUCATION);
		return ProposalResponse.of(new Proposal(owner, UUID.randomUUID(),
				"Wracamy do tego?", 40, Proposal.Source.LLM), entry, null);
	}

	/** An account already in the map, with the repository stubbed for the fire that follows. */
	private User loaded(OffsetDateTime next) {
		User account = account(next);
		when(users.findAll()).thenReturn(List.of(account));
		when(users.findById(account.getId())).thenReturn(Optional.of(account));
		scheduler.loadSchedule();
		clearInvocations(users);
		return account;
	}

	/** The id is normally Hibernate's; the schedule is keyed by it, so the test has to supply one. */
	private User account(OffsetDateTime next) {
		User account = new User(Email.of("owner-" + UUID.randomUUID() + "@example.com"),
				"{bcrypt}$2a$10$hash");
		ReflectionTestUtils.setField(account, "id", UUID.randomUUID());
		if (next != null) {
			// Set the way the id is, and for the same reason: both are Hibernate's to write, and the
			// rhythm reaches this column by a targeted update rather than through the aggregate.
			ReflectionTestUtils.setField(account, "nextProposalAt", next);
		}
		// One row updated: the account exists. The tests about a row that is gone say so by overriding
		// this with 0, which is what the update itself reports.
		when(users.scheduleNextProposalAt(eq(account.getId()), any(), any())).thenReturn(1);
		return account;
	}

	/**
	 * The moment the rhythm drew, read off the write it made rather than off the entity. The scheduler
	 * deliberately never mutates a loaded {@link User} — see
	 * {@link #movesTheRhythmOnByUpdateSoADeletedAccountCanNeverBeMergedBack()} — so the argument to the
	 * update is the only place the drawn moment appears.
	 */
	private OffsetDateTime drawnFor(User account) {
		ArgumentCaptor<OffsetDateTime> next = ArgumentCaptor.forClass(OffsetDateTime.class);
		verify(users).scheduleNextProposalAt(eq(account.getId()), next.capture(), any());
		return next.getValue();
	}

	private static long daysFromMiddayTo(OffsetDateTime moment) {
		return ChronoUnit.DAYS.between(MIDDAY.atZoneSameInstant(ProposalRhythm.USER_ZONE).toLocalDate(),
				moment.atZoneSameInstant(ProposalRhythm.USER_ZONE).toLocalDate());
	}

	private static OffsetDateTime warsaw(int hour) {
		return LocalDate.of(2026, 8, 25).atTime(hour, 0).atZone(ProposalRhythm.USER_ZONE).toOffsetDateTime();
	}
}
