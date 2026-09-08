package com.thedariusz.todoai.auth;

/**
 * Raised when {@link VerificationThrottle} refuses another code for an address (DEV-51). Carries the
 * wait so the 429 can answer {@code Retry-After} with a real number rather than a guess — the SPA
 * shows it, and a client that retries blindly is told exactly when not to.
 */
public class VerificationThrottledException extends RuntimeException {

	private final long retryAfterSeconds;

	public VerificationThrottledException(long retryAfterSeconds) {
		super("Another code cannot be sent to this address yet");
		this.retryAfterSeconds = retryAfterSeconds;
	}

	public long retryAfterSeconds() {
		return retryAfterSeconds;
	}
}
