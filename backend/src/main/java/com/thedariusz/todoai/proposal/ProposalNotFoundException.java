package com.thedariusz.todoai.proposal;

import java.util.UUID;

/**
 * The scoped lookup missed — either no such proposal exists, or it belongs to somebody else. The two
 * are conflated for the reason {@code GoalNotFoundException} spells out: the repository cannot tell
 * them apart, and an API that could would let a caller probe which ids belong to other accounts.
 *
 * <p>The id is here for the server log only; {@code ApiExceptionHandler} renders a fixed detail.
 */
public class ProposalNotFoundException extends RuntimeException {

	ProposalNotFoundException(UUID id) {
		super("No proposal " + id + " owned by the current user");
	}
}
