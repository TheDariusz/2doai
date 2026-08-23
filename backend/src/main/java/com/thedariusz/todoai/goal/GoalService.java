package com.thedariusz.todoai.goal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.security.CurrentUser;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The write and read use cases for goals and dreams, and the one place the isolation contract is
 * applied: every operation scopes itself with {@link CurrentUser#requireId()} and never trusts an id
 * from the request on its own. {@link #update} and {@link #delete} therefore load through
 * {@code findByIdAndUserId} — a foreign goal simply is not found, which is the same outcome as a
 * goal that never existed.
 */
@Service
@Transactional
class GoalService {

	private final GoalRepository goals;

	private final CurrentUser currentUser;

	GoalService(GoalRepository goals, CurrentUser currentUser) {
		this.goals = goals;
		this.currentUser = currentUser;
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
				request.horizon(), request.categoryCode());
		return GoalResponse.from(goals.saveAndFlush(goal));
	}

	/**
	 * Full replace: edit, re-categorize, convert between layers, complete and un-complete all arrive
	 * here. Flushed before mapping so the response carries the {@code @UpdateTimestamp} Hibernate
	 * assigns at flush time rather than the value the row had on load.
	 */
	GoalResponse update(UUID id, GoalUpdate request) {
		Goal goal = goals.findByIdAndUserId(id, currentUser.requireId())
				.orElseThrow(() -> new GoalNotFoundException(id));

		goal.update(request.content(), request.layer(), request.horizon(), request.categoryCode());
		if (request.completed()) {
			goal.complete(OffsetDateTime.now());
		}
		else {
			goal.reopen();
		}
		return GoalResponse.from(goals.saveAndFlush(goal));
	}

	/**
	 * Hard delete (DEV-44): the row goes, and with it the id. Soft delete would buy an undo at the
	 * cost of a {@code deleted_at} column every existing query then has to filter — S-04's "wycofane"
	 * can introduce that properly if the product turns out to want it.
	 */
	void delete(UUID id) {
		Goal goal = goals.findByIdAndUserId(id, currentUser.requireId())
				.orElseThrow(() -> new GoalNotFoundException(id));

		goals.delete(goal);
	}
}
