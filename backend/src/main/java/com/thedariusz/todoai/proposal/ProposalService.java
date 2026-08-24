package com.thedariusz.todoai.proposal;

import java.time.OffsetDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

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
 * such a query filters away, and {@code GoalRepository}'s existing finder is already the unfiltered,
 * user-scoped list {@code GET /api/goals} uses at single-user scale. Nothing new to keep {@code userId}-scoped,
 * no index on {@code due_date}, no migration. If the list ever outgrows one round-trip, a
 * {@code GET /api/goals} filter contract is where that gets solved for every reader at once —
 * S-08 deliberately did not add one, filtering by layer and category in the browser instead.
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
@Transactional(readOnly = true)
class ProposalService {

	private final GoalRepository goals;

	private final CurrentUser currentUser;

	// ponytail: one hardcoded zone while every account is Polish; a user.timezone column is the
	// upgrade, and this is the only line that reads it.
	private static final ZoneId USER_ZONE = ZoneId.of("Europe/Warsaw");

	ProposalService(GoalRepository goals, CurrentUser currentUser) {
		this.goals = goals;
		this.currentUser = currentUser;
	}

	/** @return the entry to bring back to the user, or empty when nothing has been neglected yet */
	Optional<ProposalResponse> propose() {
		List<Goal> entries = goals.findByUserIdOrderByCreatedAtDesc(currentUser.requireId());

		return ProposalSelector
				.select(entries.stream().map(Candidate::of).toList(), OffsetDateTime.now(USER_ZONE))
				.map(selection -> new ProposalResponse(GoalResponse.from(entries.stream()
						.filter(goal -> goal.getId().equals(selection.id()))
						.findFirst()
						.orElseThrow()), selection.neglectedDays()));
	}
}
