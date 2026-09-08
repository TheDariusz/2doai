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
 * <p>Text only, and that is a product decision rather than a missing feature: what this app emails is
 * a proposal's own sentence (S-05) or a six-digit code (DEV-51), and neither is worth a second MIME
 * part. The verification message deliberately carries no link at all — a confirmation link is a click
 * somebody else can make on the user's behalf.
 */
public interface EmailSender {

	/**
	 * Deliver one message. <b>Blocking, and two of its three callers are request threads</b> — sign-up
	 * and "send again" both mail inside the request that asked for it (registration and the resend
	 * endpoint); only the natural rhythm's fire is a background thread with nobody waiting on it.
	 *
	 * <p>That is a trade taken deliberately. With {@code spring.mail.*} setting the connection, read and
	 * write timeouts at 10 s each, a request thread can be held about 30 seconds before the caller gets
	 * a 503 — the cost of the alternative being a user told their account exists while the code that
	 * makes it usable is still in a queue nobody can see. What must never be held across the send is a
	 * database connection, and none is: the code is committed by its own transaction first.
	 *
	 * @param to recipient address
	 * @param subject subject line
	 * @param text plain-text body
	 * @throws MailDeliveryException if the message could not be handed to the provider
	 */
	void send(String to, String subject, String text);
}
