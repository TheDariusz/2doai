package com.thedariusz.todoai.proposal;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalResponse;

/**
 * What the engine came back with: the entry it picked, in the same shape {@code /api/goals} already
 * publishes, the silence that earned it the proposal, and the prose S-04b phrases around both.
 *
 * <p>{@code neglected_days} is not decoration — it is the engine's reason, and {@code message}
 * quotes it, which is why the number is frozen on the row rather than recomputed per read.
 * {@code source} says which arm wrote the message, so a demo can tell a real Sonnet proposal from
 * the template fallback without reading the logs.
 *
 * <p>{@code answer}, {@code answered_at} and {@code first_step} are null on a freshly created
 * proposal and filled by {@code POST /proposals/&#123;id&#125;/answer}. They are published from this
 * slice on because the answer endpoint returns <em>this</em> representation — one shape for the
 * client to render, whether it just asked for a proposal or just answered one. Serialized snake_case
 * by the global Jackson strategy.
 */
record ProposalResponse(UUID id, GoalResponse entry, long neglectedDays, String message,
		Proposal.Source source, ProposalAnswer answer, OffsetDateTime answeredAt,
		List<String> firstStep) {

	static ProposalResponse of(Proposal proposal, Goal entry) {
		return new ProposalResponse(proposal.getId(), GoalResponse.from(entry),
				proposal.getNeglectedDays(), proposal.getMessage(), proposal.getSource(),
				proposal.getAnswer(), proposal.getAnsweredAt(),
				// Phase 3 decodes the aggregate's raw first_step JSON here; nothing writes it yet.
				null);
	}
}
