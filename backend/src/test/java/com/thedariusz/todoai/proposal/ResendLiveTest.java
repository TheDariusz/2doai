package com.thedariusz.todoai.proposal;

import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalLayer;
import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.mail.MailboxProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

/**
 * One real email, actually delivered, through the transport production uses — the human-in-the-loop
 * half of FR-018 that no mock can stand in for. Gated the same way {@code ProposalLiveTest} is, so CI
 * stays hermetic and nobody's inbox is a side effect of {@code mvn test}:
 * <pre>{@code RESEND_API_KEY=… PROPOSAL_TEST_RECIPIENT=you@example.com mvn test -Dtest=ResendLiveTest}</pre>
 *
 * <p>It asserts almost nothing, on purpose. What is being checked here cannot be asserted from
 * inside the JVM: that the message <em>arrives</em> (the sender domain's DNS is verified and the
 * provider accepted it), that the Polish reads like a friend rather than a notification, and that
 * the link opens the app. A green run means the provider took the message; the verification is the
 * one happening in the inbox.
 *
 * <p>It lives here rather than in the {@code mail} package because {@link ProposalEmail} is
 * package-private, and what needs a human's eyes is the wording, not the SMTP handshake.
 */
@EnabledIfEnvironmentVariable(named = "RESEND_API_KEY", matches = ".+")
@EnabledIfEnvironmentVariable(named = "PROPOSAL_TEST_RECIPIENT", matches = ".+")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class ResendLiveTest {

	private static final Logger log = LoggerFactory.getLogger(ResendLiveTest.class);

	@Autowired
	EmailSender mail;

	@Autowired
	MailboxProperties mailbox;

	@Test
	void deliversAProposalToARealInbox() {
		ProposalResponse proposal = proposal();
		String subject = ProposalEmail.subject(proposal);
		String body = ProposalEmail.body(proposal, mailbox.baseUrl());

		log.info("Sending as {}:\nSubject: {}\n{}", mailbox.from(), subject, body);
		mail.send(System.getenv("PROPOSAL_TEST_RECIPIENT"), subject, body);
	}

	/** A proposal the template arm would have written, so this test needs no model call of its own. */
	private static ProposalResponse proposal() {
		UUID owner = UUID.randomUUID();
		Goal entry = new Goal(owner, "Oddać książkę do biblioteki", GoalLayer.TASK, null, null,
				LifeDomain.EDUCATION);
		return ProposalResponse.of(new Proposal(owner, UUID.randomUUID(),
				"W czerwcu wpisałeś: „Oddać książkę do biblioteki” — minęło 40 dni. Wracamy do tego?",
				40, Proposal.Source.TEMPLATE), entry, null);
	}
}
