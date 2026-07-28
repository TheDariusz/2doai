package com.thedariusz.todoai.auth;

/**
 * Raised when the password confirming an irreversible action does not match. Distinct from a failed
 * login on purpose: the session is valid and the caller is already authenticated, so this is a 403
 * ("you may not do this without the right password") rather than the 401 that means "log in again".
 *
 * <p>No enumeration concern applies — the caller already knows their own account exists — so unlike
 * {@link EmailAlreadyRegisteredException} the message can say what is actually wrong.
 */
public class ReAuthenticationFailedException extends RuntimeException {

	public ReAuthenticationFailedException() {
		super("The password you entered is incorrect");
	}
}
