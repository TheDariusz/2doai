package com.thedariusz.todoai.proposal;

import java.util.UUID;

import com.thedariusz.todoai.account.PerUserDataDeleter;
import org.springframework.stereotype.Component;

/**
 * FR-019 erasure of a user's proposals. Auto-discovered by {@code AccountDeletionService} through
 * its {@code List<PerUserDataDeleter>} injection, like every other deleter.
 *
 * <p><b>Do not read this as protected by the usual guard.</b> Elsewhere a forgotten deleter fails
 * loudly on a restricting FK; here it would not. {@code proposal.goal_id} carries
 * {@code ON DELETE CASCADE} (so deleting an entry with a pending proposal works), every proposal has
 * a goal, and {@code GoalDataDeleter} runs during the same account deletion — so a missing deleter
 * here would erase the rows anyway and say nothing. {@code AccountDeletionIntegrationTest} asserts
 * the erasure directly for that reason.
 *
 * <p>The cascade is also why {@link ProposalRepository#deleteByUserId} is a bulk delete: these two
 * deleters run in one transaction in an order nobody specifies, and an entity delete queued here can
 * have its rows cascaded away by the goal delete before it flushes.
 */
@Component
class ProposalDataDeleter implements PerUserDataDeleter {

	private final ProposalRepository proposals;

	ProposalDataDeleter(ProposalRepository proposals) {
		this.proposals = proposals;
	}

	@Override
	public void deleteAllForUser(UUID userId) {
		proposals.deleteByUserId(userId);
	}
}
