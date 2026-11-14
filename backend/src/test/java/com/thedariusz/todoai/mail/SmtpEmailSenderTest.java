package com.thedariusz.todoai.mail;

import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;

import jakarta.mail.Session;
import jakarta.mail.internet.MimeMessage;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.mail.MailSendException;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The boundary, asserted at the boundary: what goes out is the message the caller asked for sent from
 * the configured address, and what comes back on failure is {@link MailDeliveryException} carrying
 * enough to diagnose it and nothing that identifies anybody.
 */
class SmtpEmailSenderTest {

	private static final MailboxProperties MAILBOX =
			new MailboxProperties("2do AI <propozycje@2doai.app>", "https://2doai.app", 60);

	private final JavaMailSender transport = mock(JavaMailSender.class);

	private final SmtpEmailSender sender = new SmtpEmailSender(transport, MAILBOX);

	@Test
	void sendsTheMessageFromTheConfiguredAddress() {
		sender.send("owner@example.com", "Wróćmy do tego: Oddać książkę", "Wracamy do tego?");

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(transport).send(sent.capture());
		assertThat(sent.getValue().getFrom()).isEqualTo(MAILBOX.from());
		assertThat(sent.getValue().getTo()).containsExactly("owner@example.com");
		assertThat(sent.getValue().getSubject()).isEqualTo("Wróćmy do tego: Oddać książkę");
		assertThat(sent.getValue().getText()).isEqualTo("Wracamy do tego?");
	}

	/**
	 * The code mail goes out as two alternatives of one message: the HTML a modern client renders and
	 * the plain text a terminal client, a screen reader or a strict corporate filter falls back to.
	 */
	@Test
	void sendsTextAndHtmlAsAlternativesOfOneMessage() throws Exception {
		when(transport.createMimeMessage()).thenReturn(new MimeMessage((Session) null));

		sender.send("owner@example.com", "Kod do 2do AI: 04231789\r\nBcc: attacker@evil.com",
				"Twój kod: 04231789", "<p>Twój kod: <b>04231789</b></p>");

		ArgumentCaptor<MimeMessage> sent = ArgumentCaptor.forClass(MimeMessage.class);
		verify(transport).send(sent.capture());
		MimeMessage message = sent.getValue();
		message.saveChanges();
		ByteArrayOutputStream wire = new ByteArrayOutputStream();
		message.writeTo(wire);
		String raw = wire.toString(StandardCharsets.UTF_8);
		assertThat(message.getFrom()[0].toString()).contains("propozycje@2doai.app");
		assertThat(message.getAllRecipients()[0].toString()).isEqualTo("owner@example.com");
		assertThat(message.getSubject()).isEqualTo("Kod do 2do AI: 04231789 Bcc: attacker@evil.com");
		assertThat(raw).contains("multipart/alternative", "text/plain", "text/html");
	}

	@Test
	void translatesAnHtmlSendFailureIntoTheOneFailureCallersHandle() {
		when(transport.createMimeMessage()).thenReturn(new MimeMessage((Session) null));
		doThrow(new MailSendException("530 authentication required")).when(transport).send(any(MimeMessage.class));

		assertThatThrownBy(() -> sender.send("nowak@example.com", "Kod", "Twój kod", "<p>Twój kod</p>"))
				.isInstanceOf(MailDeliveryException.class)
				.hasMessageContaining("example.com")
				.hasMessageNotContaining("nowak");
	}

	/**
	 * A header is one line by definition, and this one quotes text the user typed — a goal, straight
	 * from {@code ProposalEmail.subject}. Jakarta Mail does defuse an embedded CRLF today, two
	 * different ways: it RFC 2047-encodes a subject carrying Polish characters (the CRLF becomes
	 * {@code =0D=0A}), and it folds a pure-ASCII one into a continuation line that unfolds back into
	 * the subject. But the second of those is {@code MimeUtility.fold}, which the
	 * {@code mail.mime.foldtext} system property switches off — set it and the same input puts a real
	 * {@code Bcc:} on the wire. Flattening here owns the invariant instead of borrowing it from a
	 * library default this app neither sets nor tests.
	 */
	@Test
	void flattensTheSubjectSoTypedNewlinesCannotBecomeHeaders() {
		sender.send("owner@example.com", "Wróćmy do tego: Kup mleko\r\nBcc: attacker@evil.com",
				"Wracamy do tego?");

		ArgumentCaptor<SimpleMailMessage> sent = ArgumentCaptor.forClass(SimpleMailMessage.class);
		verify(transport).send(sent.capture());
		assertThat(sent.getValue().getSubject())
				.isEqualTo("Wróćmy do tego: Kup mleko Bcc: attacker@evil.com")
				.doesNotContain("\r", "\n");
	}

	/**
	 * The port's whole contract on the failure path: one provider-neutral type, so the caller — a
	 * scheduler thread that must survive it and move on — never sees a Jakarta Mail or Spring type.
	 */
	@Test
	void translatesATransportFailureIntoTheOneFailureCallersHandle() {
		doThrow(new MailSendException("530 authentication required")).when(transport).send(any(SimpleMailMessage.class));

		assertThatThrownBy(() -> sender.send("owner@example.com", "Wróćmy do tego", "Wracamy?"))
				.isInstanceOf(MailDeliveryException.class)
				.hasCauseInstanceOf(MailSendException.class);
	}

	/**
	 * A delivery failure is diagnosed from a log line and nothing else, and that line is this
	 * exception's message. It has to say enough to tell a wrong key (every domain fails) from one
	 * address that bounces — and it must not say who, because the address <em>is</em> the account.
	 */
	@Test
	void namesTheDomainAndTheSizeButNeverTheRecipientOrTheProse() {
		doThrow(new MailSendException("boom")).when(transport).send(any(SimpleMailMessage.class));

		assertThatThrownBy(() -> sender.send("nowak@example.com", "Wróćmy do tego: Oddać książkę",
				"W czerwcu wpisałeś: „Oddać książkę”"))
				.hasMessageContaining("example.com")
				.hasMessageNotContaining("nowak")
				.hasMessageNotContaining("Oddać książkę");
	}
}
