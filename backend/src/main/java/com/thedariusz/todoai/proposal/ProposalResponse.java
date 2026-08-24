package com.thedariusz.todoai.proposal;

import com.thedariusz.todoai.goal.GoalResponse;

/**
 * What the engine came back with: the entry it picked, in the same shape {@code /api/goals} already
 * publishes, plus the silence that earned it the proposal.
 *
 * <p>{@code neglected_days} is not decoration — it is the engine's reason, and S-04b needs it to
 * phrase one ("dwa miesiące temu wpisałeś…"). Serialized snake_case by the global Jackson strategy.
 * Nothing here says <em>why</em> in prose: that is the LLM's half of S-04, and this slice
 * deliberately has no caller for it.
 */
record ProposalResponse(GoalResponse entry, long neglectedDays) {
}
