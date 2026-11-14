package com.thedariusz.todoai.auth;

/**
 * A code could not even be queued: the auth-mail queue is full. Raised before the address is looked
 * up, so the 503 it becomes is the same for every address. (A spent daily budget is not this: it
 * drops the code after the lookup and the request still answers 202.)
 */
public class AuthMailUnavailableException extends RuntimeException {

	public AuthMailUnavailableException() {
		super("A code cannot be sent right now");
	}
}
