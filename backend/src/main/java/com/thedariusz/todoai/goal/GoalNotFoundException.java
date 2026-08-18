package com.thedariusz.todoai.goal;

import java.util.UUID;

/**
 * The scoped lookup missed — either no such goal exists, or it belongs to somebody else. The two are
 * <b>deliberately conflated</b>: {@code GoalRepository.findByIdAndUserId} cannot tell them apart, and
 * an API that could would let a caller probe which ids belong to other accounts.
 *
 * <p>The id lives in the exception message for the server log only. {@code ApiExceptionHandler}
 * renders a fixed, generic detail — echoing this message would reintroduce exactly the leak the
 * conflation is there to prevent.
 */
public class GoalNotFoundException extends RuntimeException {

	GoalNotFoundException(UUID id) {
		super("No goal " + id + " owned by the current user");
	}
}
