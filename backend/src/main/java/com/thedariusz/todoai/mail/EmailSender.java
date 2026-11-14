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
 * a proposal's own sentence (S-05) or an eight-digit code (DEV-51, passwordless sign-in), and neither
 * is worth a second MIME part.
 *
 * <p><b>The code mail carries a link, and the link is inert.</b> Until passwordless sign-in it carried
 * none, because a confirmation link is a click somebody else can make on the user's behalf — a mail
 * scanner following every URL would confirm the address for them. The code is now the sign-in
 * credential, typed on whatever device the mail is read on, so the link earns its place: it opens the
 * code screen with the address and code filled in (in the URL fragment, which never reaches a server
 * log), and the user still presses the button. A GET spends nothing, so a scanner that follows the
 * link still signs nobody in.
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
}
