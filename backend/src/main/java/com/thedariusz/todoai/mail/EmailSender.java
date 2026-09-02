package com.thedariusz.todoai.mail;

/**
 * Gateway the rest of the app depends on to reach a user who is not looking at it (S-05, FR-018).
 *
 * <p>The same shape as {@code LlmClient}, for the same reason: the provider is an implementation
 * detail behind this port, so call sites see neither Jakarta Mail nor Spring, and the single failure
 * mode they handle is {@link MailDeliveryException}. Today one adapter speaks SMTP to Resend; the
 * decision that made that cheap was picking a provider that speaks SMTP at all, which keeps a
 * provider swap a change of credentials rather than of code.
 *
 * <p>Text only, and that is a product decision rather than a missing feature: the one thing this app
 * ever emails is a sentence and a link.
 */
public interface EmailSender {

	/**
	 * Deliver one message. Blocking — the natural rhythm's fire is already a background thread with
	 * nobody waiting on it, so an async wrapper would buy nothing and hide the failure.
	 *
	 * @param to recipient address
	 * @param subject subject line
	 * @param text plain-text body
	 * @throws MailDeliveryException if the message could not be handed to the provider
	 */
	void send(String to, String subject, String text);
}
