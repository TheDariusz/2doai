package com.thedariusz.todoai.proposal;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import com.thedariusz.todoai.ai.LlmClient;
import com.thedariusz.todoai.ai.LlmException;
import com.thedariusz.todoai.ai.LlmProperties;
import com.thedariusz.todoai.ai.memory.AiMemoryService;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalRepository;
import com.thedariusz.todoai.proposal.ProposalSelector.Candidate;
import com.thedariusz.todoai.proposal.ProposalSelector.Selection;
import com.thedariusz.todoai.security.CurrentUser;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

/**
 * The one use case of the proposals resource: hand the engine the caller's entries, phrase its
 * answer, and remember what the user was shown. Scoped by {@link CurrentUser#requireId()} like every
 * other per-user read, so the engine can only ever rank rows the caller owns.
 *
 * <p><b>A pending proposal short-circuits everything.</b> That is what at-most-one (FR-018) means
 * under a manual trigger: pressing the button again returns the same card rather than re-selecting
 * and paying for a second Sonnet call. It is also the cheapest path — one indexed read, and the
 * whole entry list is never loaded.
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
 * transaction; nothing here needs them to be one. Phase 3's answer flow does, and gets its own.
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

	// ponytail: one hardcoded zone while every account is Polish; a user.timezone column is the
	// upgrade, and this is the only line that reads it.
	private static final ZoneId USER_ZONE = ZoneId.of("Europe/Warsaw");

	private final GoalRepository goals;

	private final ProposalRepository proposals;

	private final LlmClient llm;

	private final AiMemoryService memory;

	private final CurrentUser currentUser;

	private final String model;

	ProposalService(GoalRepository goals, ProposalRepository proposals, LlmClient llm,
			AiMemoryService memory, CurrentUser currentUser, LlmProperties properties) {
		this.goals = goals;
		this.proposals = proposals;
		this.llm = llm;
		this.memory = memory;
		this.currentUser = currentUser;
		this.model = properties.model().sonnet();
	}

	/** @return the proposal to put in front of the user, or empty when nothing has been neglected */
	Optional<ProposalResponse> propose() {
		UUID userId = currentUser.requireId();

		Optional<Proposal> pending = proposals.findByUserIdAndAnsweredAtIsNull(userId);
		if (pending.isPresent()) {
			return pending.map(proposal -> ProposalResponse.of(proposal, entry(userId, proposal.getGoalId())));
		}

		List<Goal> entries = goals.findByUserIdOrderByCreatedAtDesc(userId);
		Optional<Selection> selection = ProposalSelector
				.select(entries.stream().map(Candidate::of).toList(), OffsetDateTime.now(USER_ZONE));
		if (selection.isEmpty()) {
			return Optional.empty();
		}

		Goal picked = entries.stream()
				.filter(goal -> goal.getId().equals(selection.get().id()))
				.findFirst()
				.orElseThrow();
		return Optional.of(open(userId, picked, selection.get().neglectedDays()));
	}

	/**
	 * Phrase the pick and record what was shown. The insert can still lose a race with a concurrent
	 * press — {@code idx_proposal_one_pending} is what actually enforces FR-018, precisely because a
	 * service-level check would race with itself — so a rejected insert means the other press won,
	 * and its proposal is the right answer to return rather than a 500.
	 */
	private ProposalResponse open(UUID userId, Goal entry, long neglectedDays) {
		try {
			return ProposalResponse.of(proposals.saveAndFlush(draft(userId, entry, neglectedDays)), entry);
		}
		catch (DataIntegrityViolationException ex) {
			log.info("A concurrent press already opened a proposal; returning that one");
			return proposals.findByUserIdAndAnsweredAtIsNull(userId)
					.map(winner -> ProposalResponse.of(winner, entry(userId, winner.getGoalId())))
					.orElseThrow(() -> ex);
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

	/** The entry a stored proposal points at. Always present: {@code proposal.goal_id} cascades. */
	private Goal entry(UUID userId, UUID goalId) {
		return goals.findByIdAndUserId(goalId, userId).orElseThrow();
	}
}
