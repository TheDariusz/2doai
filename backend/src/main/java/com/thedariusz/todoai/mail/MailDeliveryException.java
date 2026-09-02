package com.thedariusz.todoai.mail;

/**
 * The single, provider-neutral failure type callers of {@link EmailSender} handle — the
 * {@code LlmException} of this port. Wraps authentication failures, transport errors and rejected
 * recipients so no Jakarta Mail or Spring type leaks across the boundary.
 *
 * <p><b>Its message is written to be logged.</b> A delivery failure is diagnosed from a log line and
 * nothing else, so the message carries the recipient's domain and the subject's length — enough to
 * tell "the API key is wrong" from "that one address bounces" — and never the address itself or a
 * word of the body, which is the user's private prose about their own life.
 */
public class MailDeliveryException extends RuntimeException {

	public MailDeliveryException(String message, Throwable cause) {
		super(message, cause);
	}
}
