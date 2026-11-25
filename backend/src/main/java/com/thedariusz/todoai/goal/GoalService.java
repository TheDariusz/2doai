package com.thedariusz.todoai.goal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.security.CurrentUser;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write and read use cases for goals and dreams, and the one place the isolation contract is
 * applied: every operation scopes itself with {@link CurrentUser#requireId()} and never trusts an id
 * from the request on its own. {@link #update} and {@link #delete} therefore pair it with the id in
 * the repository call — a foreign goal simply is not found, which is the same outcome as a goal that
 * never existed.
 */
@Service
@Transactional
class GoalService {

	private final GoalRepository goals;

	private final CurrentUser currentUser;

	private final ApplicationEventPublisher events;

	GoalService(GoalRepository goals, CurrentUser currentUser, ApplicationEventPublisher events) {
		this.goals = goals;
		this.currentUser = currentUser;
		this.events = events;
	}

	@Transactional(readOnly = true)
	List<GoalResponse> list() {
		return goals.findByUserIdOrderByCreatedAtDesc(currentUser.requireId())
				.stream()
				.map(GoalResponse::from)
				.toList();
	}

	GoalResponse create(GoalCreation request) {
		Goal goal = new Goal(currentUser.requireId(), request.content(), request.layer(),
				request.horizon(), request.dueDate(), request.categoryCode());
		return GoalResponse.from(goals.saveAndFlush(goal));
	}

	/**
	 * Full replace: edit, re-categorize, convert between layers, complete and un-complete all arrive
	 * here. Flushed before mapping so the response carries the {@code @UpdateTimestamp} Hibernate
	 * assigns at flush time rather than the value the row had on load.
	 *
	 * <p>Leaving the running — completed or withdrawn while it was active — is announced as
	 * {@link GoalLeftTheRunning}, on the transition only: the full-replace PUT re-asserts both flags on
	 * every edit, and an edit of an entry that was already done has nothing new to tell anyone. The
	 * listeners run inside this transaction, so the entry and whatever reacts to it land together.
	 */
	GoalResponse update(UUID id, GoalUpdate request) {
		Goal goal = goals.findByIdAndUserId(id, currentUser.requireId())
				.orElseThrow(() -> new GoalNotFoundException(id));
		boolean wasActive = goal.isActive();

		goal.update(request.content(), request.layer(), request.horizon(), request.dueDate(),
				request.categoryCode());
		if (request.completed()) {
			goal.complete(OffsetDateTime.now());
		}
		else {
			goal.reopen();
		}
		// The same full-replace rule, and the reason FR-013's "never" is reversible: an entry comes
		// back because the client resent it as not-withdrawn, not because a route exists to undo it.
		if (request.withdrawn()) {
			goal.withdraw(OffsetDateTime.now());
		}
		else {
			goal.restore();
		}
		Goal saved = goals.saveAndFlush(goal);
		if (wasActive && !saved.isActive()) {
			events.publishEvent(new GoalLeftTheRunning(saved.getUserId(), saved.getId()));
		}
		return GoalResponse.from(saved);
	}

	/**
	 * Hard delete (DEV-44): the row goes, and with it the id. Soft delete would buy an undo at the
	 * cost of a {@code deleted_at} column every existing query then has to filter — S-04's withdrawn
	 * can introduce that properly if the product turns out to want it.
	 */
	void delete(UUID id) {
		if (goals.deleteByIdAndUserId(id, currentUser.requireId()) == 0) {
			throw new GoalNotFoundException(id);
		}
	}
}
