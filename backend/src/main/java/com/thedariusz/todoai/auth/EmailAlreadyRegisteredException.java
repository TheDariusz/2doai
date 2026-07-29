package com.thedariusz.todoai.auth;

/**
 * Raised when registration is attempted with an email that already exists. A <em>domain</em>
 * exception, not a web one: the service layer stays free of HTTP concerns and
 * {@code ApiExceptionHandler} owns the mapping to <b>409</b>.
 *
 * <p>The 409 does leak that an address is registered — an accepted trade (see the plan): without
 * email infrastructure there is no confirm-by-mail alternative, and a user whose email is taken
 * needs to be told to log in instead.
 */
public class EmailAlreadyRegisteredException extends RuntimeException {

	public EmailAlreadyRegisteredException(Throwable cause) {
		super("Email is already registered", cause);
	}
}
