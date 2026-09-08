package com.thedariusz.todoai.auth;

/**
 * Raised when registration is attempted with an email that is already in the table — whether or not
 * the account behind it ever proved the address. A <em>domain</em> exception, not a web one: the
 * service layer stays free of HTTP concerns and {@code ApiExceptionHandler} owns the mapping to
 * <b>409</b>.
 *
 * <p><b>An unverified duplicate is this too</b> (DEV-51). A draft let a second sign-up take an
 * unproved account over, so that an abandoned or typo'd sign-up would not block the address for its
 * real owner; what it also did was let whoever typed the address rewrite the password of an account
 * whose owner was at that moment reading the code that proves it. One row per address is the version
 * with no such window, and a recovery path — not a write that hands out credentials — is the answer
 * to the typo.
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
