package com.thedariusz.todoai.mail;

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

/**
 * The boundary, asserted at the boundary: what goes out is the message the caller asked for sent from
 * the configured address, and what comes back on failure is {@link MailDeliveryException} carrying
 * enough to diagnose it and nothing that identifies anybody.
 */
class SmtpEmailSenderTest {

	private static final MailboxProperties MAILBOX =
			new MailboxProperties("2do AI <propozycje@2doai.app>", "https://2doai.app");

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
