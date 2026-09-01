package com.thedariusz.todoai.proposal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import com.thedariusz.todoai.ai.LlmClient;
import com.thedariusz.todoai.ai.LlmException;
import com.thedariusz.todoai.ai.LlmProperties;
import com.thedariusz.todoai.ai.memory.AiMemoryService;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalRepository;
import com.thedariusz.todoai.proposal.ProposalSelector.Candidate;
import com.thedariusz.todoai.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import tools.jackson.databind.json.JsonMapper;

/**
 * The proposals use case: hand the engine a user's entries, phrase its answer, and remember what
 * they were shown. Every request-borne path is scoped by {@link CurrentUser#requireId()} like every
 * other per-user read, so the engine can only ever rank rows the caller owns. The one exception is
 * {@link #proposeScheduled}, which takes the user as an argument because the natural rhythm runs on
 * a thread that has no caller — it is package-private, and reached only from inside this package.
 *
 * <p><b>A pending proposal short-circuits the manual trigger.</b> That is what at-most-one (FR-018)
 * means under a button: pressing it again returns the same card rather than re-selecting and paying
 * for a second Sonnet call. It is also the cheapest path — one indexed read, and the whole entry
 * list is never loaded. The scheduled path deliberately does the opposite; see its own javadoc.
 *
 * <p><b>It reads the whole list, and deliberately adds no finder.</b> The obvious optimisation — a
 * {@code findNeglectedByUserId} carrying the thresholds into SQL — would be wrong twice over: the
 * balancing rule needs the domains the user <em>has</em> been active in, which is precisely the rows
 * such a query filters away, and {@code GoalRepository}'s existing finder is already the unfiltered,
 * user-scoped list {@code GET /api/goals} uses at single-user scale. If the list ever outgrows one
 * round-trip, a {@code GET /api/goals} filter contract is where that gets solved for every reader at
 * once — S-08 deliberately did not add one, filtering by layer and category in the browser instead.
 *
 * <p><b>No {@code @Transactional} on this method, and that is the point.</b> The Sonnet call has a
 * 60-second budget, and a surrounding transaction would pin a Hikari connection open for all of it —
 * which on a metered, scale-to-zero Neon is the exact anti-pattern {@code lessons.md} names ("idle
 * time is billed unless you actively give it up"). Each repository call opens and closes its own
 * transaction; nothing here needs them to be one.
 *
 * <p><b>{@link #answer} needs its three writes to be one, and still cannot wrap the model call.</b>
 * A {@code STARTING} answer asks Sonnet for the first step, so a method-level {@code @Transactional}
 * would hold a connection for that same 60 seconds. Instead the model is called first, outside any
 * transaction, and a {@link TransactionTemplate} scopes a transaction around the writes alone — the
 * entry's snooze or withdrawal, the proposal's answer, and the memory episode either way land
 * together or not at all.
 *
 * <p>The clock is read here rather than inside the engine, which takes it as an argument — that is
 * the whole reason the engine is testable without a clock. It is read <b>in the user's zone</b>,
 * because {@code due_date} is a {@code LocalDate} the user picked off a calendar: comparing it
 * against a UTC server's date would leave a task that went overdue at Polish midnight unflagged
 * until 02:00, and the endpoint would answer 204 "nothing gathering dust" — the one place a real
 * fault is indistinguishable from a legitimate empty answer. The instant is unchanged, so the idle
 * thresholds (whole 24h periods) are unaffected; only {@code toLocalDate()} moves.
 */
@Service
class ProposalService {

	private static final Logger log = LoggerFactory.getLogger(ProposalService.class);

	/** Whose day the clock is read in — {@link ProposalRhythm} owns the constant and the seam. */
	private static final ZoneId USER_ZONE = ProposalRhythm.USER_ZONE;

	/** The event type the memory block prints; {@code ai_memory_episode.event_type} is 64 chars. */
	private static final String ANSWERED = "proposal_answered";

	/**
	 * The machine's own closure, kept a separate event type from {@link #ANSWERED} on purpose: the
	 * memory block feeds the next proposal's prompt, and "the user said not now" and "the user said
	 * nothing at all" are different things for it to know.
	 */
	private static final String SUPERSEDED = "proposal_superseded";

	/** Starting is its own kind of interaction: a week to actually do it before anyone asks again. */
	private static final int STARTING_QUIET_DAYS = 7;

	/** "Not now" names no term, so the app picks the shortest one that is still a reprieve. */
	private static final int NOT_NOW_QUIET_DAYS = 3;

	private final GoalRepository goals;

	private final ProposalRepository proposals;

	private final LlmClient llm;

	private final AiMemoryService memory;

	private final CurrentUser currentUser;

	private final JsonMapper json;

	private final TransactionTemplate transactions;

	private final String model;

	ProposalService(GoalRepository goals, ProposalRepository proposals, LlmClient llm,
			AiMemoryService memory, CurrentUser currentUser, LlmProperties properties,
			JsonMapper json, TransactionTemplate transactions) {
		this.goals = goals;
		this.proposals = proposals;
		this.llm = llm;
		this.memory = memory;
		this.currentUser = currentUser;
		this.json = json;
		this.transactions = transactions;
		this.model = properties.model().sonnet();
	}

	/**
	 * The FR-018 slot, read rather than filled (S-05) — what the natural rhythm left waiting while
	 * the user was not looking. The only genuinely safe operation on this resource: it decides
	 * nothing, phrases nothing and writes nothing, which is what lets the app show a proposal at
	 * open without the user having asked for one.
	 *
	 * @return the caller's unanswered proposal, or empty when nothing is waiting — never a new one,
	 *         however neglected the caller's entries are
	 */
	Optional<ProposalResponse> pending() {
		return pending(currentUser.requireId());
	}

	/** @return the proposal to put in front of the user, or empty when nothing has been neglected */
	Optional<ProposalResponse> propose() {
		UUID userId = currentUser.requireId();

		Optional<ProposalResponse> existing = pending(userId);
		if (existing.isPresent()) {
			return existing;
		}

		List<Goal> entries = goals.findByUserIdOrderByCreatedAtDesc(userId);
		return ProposalSelector
				.select(entries.stream().map(Candidate::of).toList(), OffsetDateTime.now(USER_ZONE))
				.map(selection -> open(userId, pick(entries, selection.id()), selection.neglectedDays()));
	}

	/**
	 * The same engine, run for a user nobody asked on behalf of (S-05, FR-011/FR-018) — the natural
	 * rhythm's fire. {@link CurrentUser} is never consulted: a scheduler thread has no
	 * {@code SecurityContext}, and inventing one would make the scoping decorative.
	 *
	 * <p><b>It is not {@link #propose()} with an argument, and the difference is the whole method.</b>
	 * The manual trigger short-circuits on a pending proposal — a second press must return the same
	 * card rather than pay for a second model call. Doing that here would mean the rhythm stops dead
	 * the first time the user ignores a proposal, which is exactly the user this feature exists for.
	 * So the scheduled path <em>replaces</em> instead: the unanswered proposal is closed as
	 * {@code SUPERSEDED} and the new one takes the pending slot.
	 *
	 * <p><b>Selection runs first, with the ignored entry excluded, and superseding happens only if it
	 * produced something.</b> Both halves of that are load-bearing. Superseding first would snooze the
	 * old entry and could then leave the user with nothing at all when selection came back empty —
	 * FR-018's implicit "not now" happens the moment a proposal <em>replaces</em> it, not merely
	 * because the clock struck. And excluding the entry is what stops the friend from asking about
	 * the same thing twice in a row, which is how being ignored the first time would read.
	 *
	 * @return the proposal that was opened, or empty when nothing has been neglected — in which case
	 *         the pending proposal, if any, is left exactly as it was
	 */
	Optional<ProposalResponse> proposeScheduled(UUID userId) {
		Optional<Proposal> ignored = proposals.findByUserIdAndAnsweredAtIsNull(userId);
		UUID ignoredEntry = ignored.map(Proposal::getGoalId).orElse(null);

		List<Goal> entries = goals.findByUserIdOrderByCreatedAtDesc(userId);
		List<Candidate> candidates = entries.stream()
				.filter(entry -> !entry.getId().equals(ignoredEntry))
				.map(Candidate::of)
				.toList();

		OffsetDateTime now = OffsetDateTime.now(USER_ZONE);
		return ProposalSelector.select(candidates, now).map(selection -> {
			// Before the insert, never after: the pending slot is a partial unique index, and the
			// replacement cannot be written while the proposal it replaces still holds it.
			ignored.ifPresent(proposal -> supersede(userId, proposal.getId(), now));
			return open(userId, pick(entries, selection.id()), selection.neglectedDays());
		});
	}

	/**
	 * Close the ignored proposal and quiet its entry for the same three days a spoken "not now" buys
	 * — one transaction, like {@link #answer}'s, so a machine closure can no more half-land than a
	 * user's can. The snooze goes on the {@code goal} row for the reason every other answer's does:
	 * ignoring a proposal <em>is</em> the user's response to it, and {@code ProposalSelector} reads
	 * that column as "when they last engaged".
	 *
	 * <p>Re-read under the write lock rather than trusting the copy the caller selected against: the
	 * user can answer between the two, over HTTP, while this thread is mid-cycle. Their answer is the
	 * real one — so a proposal that is no longer pending is left alone and the new proposal simply
	 * takes the freed slot, instead of {@link Proposal#supersede} throwing and losing the fire.
	 */
	private void supersede(UUID userId, UUID proposalId, OffsetDateTime now) {
		transactions.executeWithoutResult(status -> {
			Proposal current = proposals.findWithLockByIdAndUserId(proposalId, userId).orElse(null);
			if (current == null || !current.isPending()) {
				log.info("Proposal {} was answered before the rhythm could supersede it", proposalId);
				return;
			}
			Goal entry = entry(userId, current.getGoalId());

			entry.snoozeUntil(now.toLocalDate().plusDays(NOT_NOW_QUIET_DAYS));
			current.supersede(now);
			goals.saveAndFlush(entry);
			proposals.saveAndFlush(current);
			memory.record(userId, SUPERSEDED, json.writeValueAsString(
					Map.of("answer", ProposalAnswer.SUPERSEDED.name(), "entry", entry.getContent())));
		});
	}

	/** The user's open proposal, if they have one, rendered with the entry it points at. */
	private Optional<ProposalResponse> pending(UUID userId) {
		return proposals.findByUserIdAndAnsweredAtIsNull(userId)
				.map(proposal -> render(proposal, entry(userId, proposal.getGoalId())));
	}

	/** The picked entry, back out of the list its candidate was built from. */
	private static Goal pick(List<Goal> entries, UUID id) {
		return entries.stream().filter(goal -> goal.getId().equals(id)).findFirst()
				.orElseThrow(() -> new IllegalStateException(
						"The selector returned an id that is not in the list it was given: " + id));
	}

	/**
	 * Phrase the pick and record what was shown. The insert can still lose a race with a concurrent
	 * press — {@code idx_proposal_one_pending} is what actually enforces FR-018, precisely because a
	 * service-level check would race with itself — so a rejected insert means the other press won,
	 * and its proposal is the right answer to return rather than a 500.
	 */
	private ProposalResponse open(UUID userId, Goal entry, long neglectedDays) {
		try {
			return render(proposals.saveAndFlush(draft(userId, entry, neglectedDays)), entry);
		}
		catch (DataIntegrityViolationException ex) {
			// Logged with the violation itself: this arm recovers from the pending-slot index, but it
			// is written to catch every integrity failure, and without the cause it would be the one
			// place an unexpected constraint could answer 200 and leave no trace of what broke. No
			// pending row means it was never the race, and the original exception is what propagates.
			log.info("The insert lost the pending slot; returning the proposal that won it", ex);
			return pending(userId).orElseThrow(() -> ex);
		}
	}

	/** Phrase the entry, recording on the row which arm actually wrote the sentence. */
	private Proposal draft(UUID userId, Goal entry, long neglectedDays) {
		try {
			String message = llm.complete(
					ProposalPrompt.forProposal(model, memory.renderFor(userId), entry, neglectedDays));
			return new Proposal(userId, entry.getId(), message, neglectedDays, Proposal.Source.LLM);
		}
		catch (LlmException ex) {
			// The user gets a proposal either way — the roadmap's 08.09 gate, permanently wired in.
			log.warn("Falling back to the template proposal: the model call failed", ex);
			return new Proposal(userId, entry.getId(), ProposalTemplate.phrase(entry, neglectedDays),
					neglectedDays, Proposal.Source.TEMPLATE);
		}
	}

	/**
	 * Record one of FR-013's four responses and apply it to the entry, so "every answer shapes what
	 * is proposed next" is a mechanism rather than a promise. Three of the four write
	 * {@code goal.remind_after} and differ only in the term they pick; the fourth withdraws the entry
	 * outright. All four also write an episode, which is how the <em>next</em> proposal's prompt gets
	 * to know what the user said about the last one.
	 *
	 * <p>The pending check is here as well as in {@link Proposal#answer} on purpose: the aggregate's
	 * refusal is the invariant, but by the time it fired the model call would already have been paid
	 * for. Checking first turns a double-clicked answer into a 409 that costs nothing.
	 *
	 * <p><b>Both rows are then read again inside the transaction</b>, the proposal under a write lock.
	 * The copies the check above ran against are detached the moment their finders return, and a
	 * {@code STARTING} answer spends up to a Sonnet timeout in between — so merging those snapshots
	 * back would overwrite whatever landed during the call: a second answer (a withdrawal quietly
	 * becoming a snooze) or an ordinary edit of the entry. The lock is what lets the aggregate's
	 * refusal fire against current state rather than a snapshot, so two answers in flight queue and
	 * the loser gets the same 409 the pre-check hands a double-click.
	 *
	 * @throws ProposalNotFoundException if no such proposal is owned by the caller
	 * @throws ProposalAlreadyAnsweredException if it already carries an answer
	 */
	ProposalResponse answer(UUID id, ProposalAnswerRequest request) {
		UUID userId = currentUser.requireId();

		Proposal shown = proposals.findByIdAndUserId(id, userId)
				.orElseThrow(() -> new ProposalNotFoundException(id));
		if (!shown.isPending()) {
			throw new ProposalAlreadyAnsweredException(id);
		}

		// Before the transaction opens, never inside it — see the class javadoc.
		List<String> steps = request.answer() == ProposalAnswer.STARTING
				? firstStep(userId, entry(userId, shown.getGoalId())) : null;

		OffsetDateTime now = OffsetDateTime.now(USER_ZONE);
		return transactions.execute(status -> {
			Proposal current = proposals.findWithLockByIdAndUserId(id, userId)
					.orElseThrow(() -> new ProposalNotFoundException(id));
			Goal entry = entry(userId, current.getGoalId());

			quiet(entry, request, now);
			current.answer(request.answer(), now);
			if (steps != null) {
				current.recordFirstStep(json.writeValueAsString(steps));
			}
			Goal quieted = goals.saveAndFlush(entry);
			Proposal answered = proposals.saveAndFlush(current);
			memory.record(userId, ANSWERED, json.writeValueAsString(
					Map.of("answer", request.answer().name(), "entry", entry.getContent())));
			return render(answered, quieted);
		});
	}

	/**
	 * Apply the answer to the entry — the user-performed half, which is why it lands on the
	 * {@code goal} row and moves its {@code updated_at}. {@code REMIND_LATER}'s term is already known
	 * to be one of the offered presets: {@code ProposalAnswerRequest} rejects anything else with 422
	 * before this is reached.
	 */
	private static void quiet(Goal entry, ProposalAnswerRequest request, OffsetDateTime now) {
		LocalDate today = now.toLocalDate();
		switch (request.answer()) {
			case STARTING -> entry.snoozeUntil(today.plusDays(STARTING_QUIET_DAYS));
			case NOT_NOW -> entry.snoozeUntil(today.plusDays(NOT_NOW_QUIET_DAYS));
			case REMIND_LATER -> entry.snoozeUntil(today.plusDays(request.remindInDays()));
			case NEVER -> entry.withdraw(now);
		}
	}

	/**
	 * FR-014's bullets, asked for once and stored. A failure here is not the user's problem — they
	 * answered, and the answer must land — so it degrades to an empty list, which the contract
	 * publishes as "the answer landed but the model did not".
	 */
	private List<String> firstStep(UUID userId, Goal entry) {
		try {
			return llm.completeStructured(
					ProposalPrompt.forFirstStep(model, memory.renderFor(userId), entry),
					FirstStep.class, FirstStep.SCHEMA).steps();
		}
		catch (LlmException ex) {
			log.warn("The first step could not be generated; the answer still lands", ex);
			return List.of();
		}
	}

	/** Decode the stored bullets, so the representation is built the same way on every path. */
	private ProposalResponse render(Proposal proposal, Goal entry) {
		List<String> steps = proposal.getFirstStep() == null ? null
				: List.of(json.readValue(proposal.getFirstStep(), String[].class));
		return ProposalResponse.of(proposal, entry, steps);
	}

	/**
	 * The entry a stored proposal points at. Always present: {@code proposal.goal_id} cascades, so a
	 * miss here is the schema failing rather than the caller asking for something that never existed
	 * — which is why it says so instead of throwing the bare {@code NoSuchElementException} that
	 * would reach the handler as a 500 with nothing in it.
	 */
	private Goal entry(UUID userId, UUID goalId) {
		return goals.findByIdAndUserId(goalId, userId).orElseThrow(() -> new IllegalStateException(
				"Proposal points at goal " + goalId + ", which user " + userId + " does not own"));
	}
}
