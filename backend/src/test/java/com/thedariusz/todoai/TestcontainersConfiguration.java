package com.thedariusz.todoai;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.mail.MailDeliveryException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gives {@code @SpringBootTest} a real Postgres. {@code @ServiceConnection}
 * auto-wires the container's JDBC coordinates into the context, so Flyway runs
 * and Hibernate validates against the same DB used in dev and production.
 * {@code @WebMvcTest} slices (e.g. PingControllerTest) load no datasource and
 * are unaffected.
 *
 * <p>It also replaces the SMTP adapter, which since DEV-51 is on the path of every registration
 * rather than only of the natural rhythm's fire. The fake is what keeps the suite hermetic — no run
 * of {@code mvn test} reaches smtp.resend.com — and it is also the only way a test can read the code
 * it was just sent, which is how {@code ApiTestBase} produces a usable account.
 */
@TestConfiguration(proxyBeanMethods = false)
public class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Testcontainers 2.x: PostgreSQLContainer is no longer generic (the
		// recursive <SELF> type parameter from 1.x was dropped).
		return new PostgreSQLContainer("postgres:18");
	}

	/**
	 * {@code @Primary} because {@code SmtpEmailSender} is a {@code @Component} and stays in the
	 * context; without it every injection point sees two candidates. A test that wants the real
	 * transport asks for it by name ({@code ResendLiveTest}).
	 */
	@Bean
	@Primary
	RecordingEmailSender emailSender() {
		return new RecordingEmailSender();
	}

	/**
	 * Every message the app tried to send, and — because a verification code only ever exists inside
	 * one — the code each address was last given. Reading it back out of the message is deliberate:
	 * the alternative is a seam in production code that exists only for tests, and this way the code a
	 * test types is the code a user would have read.
	 */
	public static class RecordingEmailSender implements EmailSender {

		/** The one six-digit run in a verification message; nothing else the app sends has one. */
		private static final Pattern CODE = Pattern.compile("\\b\\d{6}\\b");

		private final List<Sent> sent = new CopyOnWriteArrayList<>();

		private final AtomicBoolean failNext = new AtomicBoolean();

		@Override
		public void send(String to, String subject, String text) {
			if (this.failNext.compareAndSet(true, false)) {
				throw new MailDeliveryException("a forced failure to a @"
						+ StringUtils.substringAfterLast(to, '@') + " address", new IllegalStateException("forced"));
			}
			this.sent.add(new Sent(to, subject, text));
		}

		/**
		 * Make the next send fail the way a provider outage does, once. The 503 it produces is the one
		 * response with a committed account behind it, and nothing else in the suite can reach it.
		 */
		public void failNextSend() {
			this.failNext.set(true);
		}

		public List<Sent> sent() {
			return List.copyOf(this.sent);
		}

		public void clear() {
			this.sent.clear();
		}

		/** The code most recently mailed to this address, or empty if it was never sent one. */
		public Optional<String> codeFor(String email) {
			return this.sent.reversed().stream()
					.filter(message -> message.to().equalsIgnoreCase(email))
					.map(message -> CODE.matcher(message.subject()))
					.filter(Matcher::find)
					.map(Matcher::group)
					.findFirst();
		}

		public boolean sentAnythingTo(String email) {
			return this.sent.stream().anyMatch(message -> message.to().equalsIgnoreCase(email));
		}

		public record Sent(String to, String subject, String text) {
		}
	}
}
