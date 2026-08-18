package com.thedariusz.todoai.goal;

import java.util.UUID;

import com.thedariusz.todoai.account.PerUserDataDeleter;
import org.springframework.stereotype.Component;

/**
 * FR-019 erasure of a user's goals and dreams. Auto-discovered by {@code AccountDeletionService}
 * through its {@code List<PerUserDataDeleter>} injection — no registration step to forget, and if it
 * were absent the account delete would fail loudly on {@code goal}'s restricting FK rather than
 * orphaning the rows.
 */
@Component
class GoalDataDeleter implements PerUserDataDeleter {

	private final GoalRepository goals;

	GoalDataDeleter(GoalRepository goals) {
		this.goals = goals;
	}

	@Override
	public void deleteAllForUser(UUID userId) {
		goals.deleteByUserId(userId);
	}
}
