package com.thedariusz.todoai.user;

/**
 * What the one outstanding e-mailed code on an account is for. The account has a single code slot,
 * so issuing one kind of code replaces the other; a code redeemed for the purpose it was not drawn
 * for is refused like a wrong one.
 *
 * <p>Stored by name in {@code app_user.verification_purpose} ({@code V15}), so a constant here is a
 * wire value in the database: rename one and existing rows stop reading.
 */
public enum CodePurpose {

	/** Opens a session, and on an unproved account also proves the address. */
	SIGN_IN,

	/** Confirms the irreversible deletion of the account (issued from release 2 on). */
	ACCOUNT_DELETION
}
