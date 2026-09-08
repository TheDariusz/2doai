package com.thedariusz.todoai.auth;

/**
 * Raised when a submitted code does not prove the address (DEV-51).
 *
 * <p><b>One type and one fixed message for six different causes</b> — no such account, already
 * verified, no code outstanding, expired, out of attempts, or simply wrong. Telling them apart would
 * hand an unauthenticated caller an oracle for which addresses have accounts and which of those are
 * still unproved, and none of the six leaves the user anything different to do: ask for a new code.
 */
public class VerificationFailedException extends RuntimeException {

	public VerificationFailedException() {
		super("The code is wrong or no longer valid");
	}
}
