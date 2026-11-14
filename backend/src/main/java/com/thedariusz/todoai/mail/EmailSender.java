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
 * <p>Two shapes. A proposal (S-05) is one sentence and goes out as plain text. The sign-in code mail
 * (DEV-51, DEV-62) is the first thing a new user receives from the app, so it carries an HTML
 * alternative with the app's mark, a button and the code set large — the plain text stays alongside
 * it for every client that will not render HTML.
 *
 * <p><b>The code mail's link spends nothing on a GET.</b> It opens the code screen with the address
 * and code in the URL fragment, which never reaches a server log. The SPA signs in from it on its own
 * only on the device that asked for the code; anywhere else — a mail scanner's browser included — it
 * waits for a press. See {@code SignInLink}.
 */
public interface EmailSender {

	/**
	 * Deliver one message. <b>Blocking</b>, so where it runs is the caller's decision: sign-up still
	 * mails on its request thread and reports a provider failure as 503; a code request mails on the
	 * auth-mail executor ({@code MailConfig}), so the response time is the same for every address; the
	 * natural rhythm's fire is a background thread with nobody waiting on it.
	 *
	 * <p>With {@code spring.mail.*} setting the connection, read and write timeouts at 10 s each, a
	 * thread can be held about 30 seconds. What must never be held across the send is a database
	 * connection, and none is: the code is committed by its own transaction first.
	 *
	 * @param to recipient address
	 * @param subject subject line
	 * @param text plain-text body
	 * @throws MailDeliveryException if the message could not be handed to the provider
	 */
	void send(String to, String subject, String text);

	/**
	 * Deliver one message as two alternatives: {@code html} for clients that render it, {@code text}
	 * for the rest. Same blocking contract and the same single failure as the text-only overload.
	 *
	 * @param html the HTML body — a complete document, already escaped by its author
	 * @throws MailDeliveryException if the message could not be handed to the provider
	 */
	void send(String to, String subject, String text, String html);
}
