package com.thedariusz.todoai;

import java.sql.Connection;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.Executor;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.sql.DataSource;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.thedariusz.todoai.auth.EmailVerificationService;
import com.thedariusz.todoai.mail.EmailSender;
import com.thedariusz.todoai.mail.MailConfig;
import com.thedariusz.todoai.mail.MailDeliveryException;
import org.apache.commons.lang3.StringUtils;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.config.BeanPostProcessor;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.core.task.SyncTaskExecutor;
import org.springframework.jdbc.datasource.DelegatingDataSource;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gives {@code @SpringBootTest} a real Postgres. {@code @ServiceConnection}
 * auto-wires the container's JDBC coordinates into the context, so Flyway runs
 * and Hibernate validates against the same DB used in dev and production.
 * {@code @WebMvcTest} slices (none today) load no datasource and
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
	 * Runs auth mail on the calling thread. Production sends it from a queue so the response time is
	 * the same for every address; here about forty call sites read the mailed code the moment the
	 * request returns, and a queue would make every one of them race the worker. {@code @Primary} under
	 * the production qualifier, rather than the same bean name, because Boot refuses to override a
	 * bean definition.
	 */
	@Bean
	@Primary
	@Qualifier(MailConfig.AUTH_MAIL_EXECUTOR)
	Executor synchronousAuthMailExecutor() {
		return new SyncTaskExecutor();
	}

	/**
	 * Counts every connection the application takes from the pool, so a test can say "this request
	 * did not touch the database" out loud. A connection is the honest unit: nothing reaches Postgres
	 * without one, whether through JPA, a {@code JdbcTemplate} or Flyway, and on Neon each one is what
	 * resets the idle timer (lessons.md, "Let a scale-to-zero database actually sleep").
	 *
	 * <p>Static, as a {@code BeanPostProcessor} must be, so it does not drag this configuration into
	 * early initialisation. The wrapper is a {@link DelegatingDataSource}, which Boot's pool-metadata
	 * lookup unwraps, so the pool underneath is still visible as the Hikari pool it is.
	 */
	@Bean
	static BeanPostProcessor connectionCountingDataSource() {
		return new BeanPostProcessor() {
			@Override
			public Object postProcessAfterInitialization(Object bean, String beanName) {
				return bean instanceof DataSource dataSource && !(bean instanceof ConnectionCounter)
						? new ConnectionCounter(dataSource)
						: bean;
			}
		};
	}

	/** The pool as the application sees it, plus how many connections it has handed out so far. */
	public static class ConnectionCounter extends DelegatingDataSource {

		private final AtomicLong borrowed = new AtomicLong();

		ConnectionCounter(DataSource target) {
			super(target);
		}

		@Override
		public Connection getConnection() throws SQLException {
			this.borrowed.incrementAndGet();
			return super.getConnection();
		}

		@Override
		public Connection getConnection(String username, String password) throws SQLException {
			this.borrowed.incrementAndGet();
			return super.getConnection(username, password);
		}

		/** Connections handed out since the context started; compare two readings around a request. */
		public long borrowed() {
			return this.borrowed.get();
		}
	}

	/**
	 * Every message the app tried to send, and — because a verification code only ever exists inside
	 * one — the code each address was last given. Reading it back out of the message is deliberate:
	 * the alternative is a seam in production code that exists only for tests, and this way the code a
	 * test types is the code a user would have read.
	 */
	public static class RecordingEmailSender implements EmailSender {

		/** The one code-wide run of digits in a code message's subject; nothing else the app sends has one. */
		private static final Pattern CODE =
				Pattern.compile("\\b\\d{" + EmailVerificationService.CODE_LENGTH + "}\\b");

		private final List<Sent> sent = new CopyOnWriteArrayList<>();

		private final AtomicBoolean failNext = new AtomicBoolean();

		@Override
		public void send(String to, String subject, String text) {
			send(to, subject, text, null);
		}

		@Override
		public void send(String to, String subject, String text, String html) {
			if (this.failNext.compareAndSet(true, false)) {
				throw new MailDeliveryException("a forced failure to a @"
						+ StringUtils.substringAfterLast(to, '@') + " address", new IllegalStateException("forced"));
			}
			this.sent.add(new Sent(to, subject, text, html));
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

		/** @param html the HTML alternative, or {@code null} for a text-only message */
		public record Sent(String to, String subject, String text, String html) {
		}
	}
}
