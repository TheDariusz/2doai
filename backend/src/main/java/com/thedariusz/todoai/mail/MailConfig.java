package com.thedariusz.todoai.mail;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

/**
 * Mail wiring seam. Mirrors {@code MemoryConfig} and {@code ProposalConfig}: it enables
 * {@link MailboxProperties} binding, so a typo in a key fails the build rather than shipping a sender
 * address nobody verified — and it owns the executor auth mail is sent on.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailboxProperties.class)
public class MailConfig {

	/**
	 * The qualifier the auth-mail executor is injected by. A qualifier rather than a bean name so the
	 * test configuration can supply a synchronous executor under it ({@code @Primary}) without
	 * overriding a bean definition, which Boot refuses by default.
	 */
	public static final String AUTH_MAIL_EXECUTOR = "authMail";

	/**
	 * Where a code request does everything after its throttle and budget checks: the address lookup,
	 * the code write and the SMTP send. Off the request thread so the response takes the same time for
	 * every address — an SMTP round trip that only known addresses pay would undo the uniform 202.
	 *
	 * <p>One worker and a bounded queue: auth mail is rare, ordering does not matter, and a burst must
	 * not grow memory without limit on the 512 MB machine. A full queue is refused at submission, which
	 * the caller answers with 503. Queued sends finish on shutdown, so a deploy does not drop a code a
	 * user is waiting for.
	 */
	@Bean
	@Qualifier(AUTH_MAIL_EXECUTOR)
	ThreadPoolTaskExecutor authMailExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(1);
		executor.setMaxPoolSize(1);
		executor.setQueueCapacity(100);
		executor.setThreadNamePrefix("auth-mail-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(30);
		return executor;
	}
}
