package com.thedariusz.todoai.ai;

/**
 * The single, provider-neutral failure type callers of {@link LlmClient} handle. Wraps
 * transport errors, non-2xx responses (after retries are exhausted), and malformed or
 * undeserializable responses so no Spring/HTTP types leak across the port boundary.
 */
public class LlmException extends RuntimeException {

	public LlmException(String message) {
		super(message);
	}

	public LlmException(String message, Throwable cause) {
		super(message, cause);
	}
}
