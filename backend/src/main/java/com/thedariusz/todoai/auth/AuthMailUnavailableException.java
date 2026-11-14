package com.thedariusz.todoai.auth;

/**
 * A code could not even be queued: today's auth-mail budget is spent, or the auth-mail queue is full.
 * Raised before the address is looked up, so the 503 it becomes is the same for every address.
 */
public class AuthMailUnavailableException extends RuntimeException {

	public AuthMailUnavailableException() {
		super("A code cannot be sent right now");
	}
}
