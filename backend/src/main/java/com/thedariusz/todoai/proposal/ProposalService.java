package com.thedariusz.todoai.proposal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalRepository;
import com.thedariusz.todoai.goal.GoalResponse;
import com.thedariusz.todoai.proposal.ProposalSelector.Candidate;
import com.thedariusz.todoai.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The one use case of the proposals resource: hand the engine the caller's entries and turn its
 * answer into a representation. Scoped by {@link CurrentUser#requireId()} like every other per-user
 * read, so the engine can only ever rank rows the caller owns.
 *
 * <p><b>It reads the whole list, and deliberately adds no finder.</b> The obvious optimisation — a
 * {@code findNeglectedByUserId} carrying the thresholds into SQL — would be wrong twice over: the
 * balancing rule needs the domains the user <em>has</em> been active in, which is precisely the rows
 * such a query filters away, and {@code GoalRepository}'s existing unparameterized list is already
 * the shape {@code GET /api/goals} uses at single-user scale. Nothing new to keep {@code userId}-scoped,
 * no index on {@code due_date}, no migration. If the list ever outgrows one round-trip, S-08's filter
 * contract is where that gets solved for every reader at once.
 *
 * <p>{@code OffsetDateTime.now()} is read here rather than inside the engine, which takes it as an
 * argument — that is the whole reason the engine is testable without a clock.
 */
@Service
@Transactional(readOnly = true)
class ProposalService {

	private final GoalRepository goals;

	private final CurrentUser currentUser;

	ProposalService(GoalRepository goals, CurrentUser currentUser) {
		this.goals = goals;
		this.currentUser = currentUser;
	}

	/** @return the entry to bring back to the user, or empty when nothing has been neglected yet */
	Optional<ProposalResponse> propose() {
		List<Goal> entries = goals.findByUserIdOrderByCreatedAtDesc(currentUser.requireId());
		Map<UUID, Goal> byId = entries.stream()
				.collect(Collectors.toMap(Goal::getId, Function.identity()));

		return ProposalSelector
				.select(entries.stream().map(Candidate::of).toList(), OffsetDateTime.now())
				.map(selection -> new ProposalResponse(GoalResponse.from(byId.get(selection.id())),
						selection.neglectedDays()));
	}
}
