package com.thedariusz.todoai.auth;

/**
 * Raised when a submitted code does not prove the address (DEV-51).
 *
 * <p><b>One type and one fixed message for every cause {@code EmailVerificationService.redeem}
 * refuses for</b> — no such account, no code outstanding, a code drawn for another purpose, expired,
 * out of attempts, spent by a racing request, or simply wrong. Telling them apart would hand an
 * unauthenticated caller an oracle for which addresses have accounts, and none of them leaves the user
 * anything different to do: ask for a new code. The service logs which one it was.
 */
public class VerificationFailedException extends RuntimeException {

	public VerificationFailedException() {
		super("The code is wrong or no longer valid");
	}
}
