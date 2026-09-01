package com.thedariusz.todoai.mail;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Component;

/**
 * {@link EmailSender} over SMTP — Resend today, configured entirely in {@code spring.mail.*}, which
 * is what keeps the provider a credentials choice rather than a dependency.
 *
 * <p>The adapter's whole job is the translation at the boundary: a {@link SimpleMailMessage} going
 * out, a {@link MailDeliveryException} coming back. Retries and timeouts belong to the transport, and
 * the one caller — the natural rhythm's fire — is deliberately not built to retry: the in-app card is
 * the guaranteed channel, so an email that does not arrive costs the user a nudge, not the proposal.
 *
 * <p><b>Nothing it logs identifies anybody.</b> The proposal is the app's most personal sentence —
 * the user's own goal, quoted back — and the address is the account itself, so both lines below carry
 * the recipient's <em>domain</em> and the subject's <em>length</em> and stop there. That is still
 * enough to read a failure: a bad key fails for every domain at once, a bounce fails for one.
 */
@Component
class SmtpEmailSender implements EmailSender {

	private static final Logger log = LoggerFactory.getLogger(SmtpEmailSender.class);

	private final JavaMailSender transport;

	private final MailboxProperties properties;

	SmtpEmailSender(JavaMailSender transport, MailboxProperties properties) {
		this.transport = transport;
		this.properties = properties;
	}

	@Override
	public void send(String to, String subject, String text) {
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.from());
		message.setTo(to);
		message.setSubject(subject);
		message.setText(text);
		try {
			transport.send(message);
		}
		catch (MailException ex) {
			throw new MailDeliveryException(describe(to, subject), ex);
		}
		// The app's only unprompted act, and the only evidence it happened: worth an INFO line, in
		// prod as much as in the local smoke run.
		log.info("Delivered: {}", describe(to, subject));
	}

	/** Who and how big, never what — see the class javadoc. */
	private static String describe(String to, String subject) {
		return "a %d-char subject to a @%s address".formatted(subject.length(),
				StringUtils.substringAfterLast(to, '@'));
	}
}
