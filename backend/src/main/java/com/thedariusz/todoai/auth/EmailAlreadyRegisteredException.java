package com.thedariusz.todoai.auth;

/**
 * Raised when registration is attempted with an email that already belongs to a <b>verified</b>
 * account. A <em>domain</em> exception, not a web one: the service layer stays free of HTTP concerns
 * and {@code ApiExceptionHandler} owns the mapping to <b>409</b>.
 *
 * <p>An unverified duplicate is not this — it is taken over instead (DEV-51), which is what keeps an
 * abandoned or typo'd sign-up from blocking the address for its real owner.
 *
 * <p>The 409 does leak that an address is registered — an accepted trade (see the plan): a user
 * whose email is taken needs to be told to log in instead, and the alternative is a sign-up screen
 * that cannot tell them anything at all.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

	private static final String MESSAGE = "Email is already registered";

	public EmailAlreadyRegisteredException() {
		super(MESSAGE);
	}

	public EmailAlreadyRegisteredException(Throwable cause) {
		super(MESSAGE, cause);
	}
}
