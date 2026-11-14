package com.thedariusz.todoai.mail;

import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import jakarta.mail.MessagingException;
import jakarta.mail.internet.MimeMessage;

import org.springframework.mail.MailException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.stereotype.Component;

/**
 * {@link EmailSender} over SMTP — Resend today, configured entirely in {@code spring.mail.*}, which
 * is what keeps the provider a credentials choice rather than a dependency.
 *
 * <p>The adapter's whole job is the translation at the boundary: a {@link SimpleMailMessage} (or, with
 * an HTML alternative, a {@link MimeMessage}) going out, a {@link MailDeliveryException} coming back. Retries and timeouts belong to the transport, and
 * no caller is built to retry: the natural rhythm's fire has the in-app card as its guaranteed
 * channel, so an email that does not arrive costs the user a nudge rather than the proposal, and the
 * verification service answers 503 and leaves the retry to the "send again" the user already has.
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
		// A header is one line, and this one quotes text the user typed. Jakarta Mail defuses an
		// embedded CRLF today — it RFC 2047-encodes a subject with Polish in it, and folds a
		// pure-ASCII one into a continuation line — but the second of those is MimeUtility.fold,
		// one mail.mime.foldtext system property from off. Owning the invariant costs a line.
		String header = StringUtils.normalizeSpace(subject);
		SimpleMailMessage message = new SimpleMailMessage();
		message.setFrom(properties.from());
		message.setTo(to);
		message.setSubject(header);
		message.setText(text);
		try {
			transport.send(message);
		}
		catch (MailException ex) {
			throw new MailDeliveryException(describe(to, header), ex);
		}
		delivered(to, header);
	}

	@Override
	public void send(String to, String subject, String text, String html) {
		String header = StringUtils.normalizeSpace(subject);
		try {
			MimeMessage message = transport.createMimeMessage();
			// multipart = true is MULTIPART_MODE_MIXED_RELATED: a multipart/mixed root around a
			// multipart/related, which setText(plain, html) fills with a multipart/alternative. Inside
			// it the client picks the last part it can render, so the HTML goes second.
			MimeMessageHelper helper = new MimeMessageHelper(message, true, "UTF-8");
			helper.setFrom(properties.from());
			helper.setTo(to);
			helper.setSubject(header);
			helper.setText(text, html);
			transport.send(message);
		}
		catch (MailException | MessagingException ex) {
			throw new MailDeliveryException(describe(to, header), ex);
		}
		delivered(to, header);
	}

	/**
	 * The app's only unprompted act, and the only evidence it happened: worth an INFO line, in prod as
	 * much as in the local smoke run.
	 */
	private static void delivered(String to, String header) {
		log.info("Delivered: {}", describe(to, header));
	}

	/** Who and how big, never what — see the class javadoc. */
	private static String describe(String to, String subject) {
		return "a %d-char subject to a @%s address".formatted(subject.length(),
				StringUtils.substringAfterLast(to, '@'));
	}
}
