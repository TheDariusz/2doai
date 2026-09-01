package com.thedariusz.todoai.mail;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link MailboxProperties} binds from the committed {@code application.properties} (relaxed
 * binding of {@code app.mail.base-url} → {@code baseUrl()}) — so a typo in a key fails the build
 * rather than shipping emails from nobody, pointing nowhere. Mirrors {@code RhythmPropertiesTest};
 * no auto-configuration runs, so no datasource and no SMTP transport are needed.
 */
@SpringJUnitConfig(MailboxPropertiesTest.Config.class)
@TestPropertySource(locations = "classpath:application.properties")
class MailboxPropertiesTest {

	@Autowired
	MailboxProperties properties;

	@Test
	void bindsTheSenderFromApplicationProperties() {
		assertThat(properties.from()).isEqualTo("2do AI <propozycje@2doai.app>");
	}

	/**
	 * Asserted by shape rather than by value: the committed setting is a placeholder over
	 * {@code APP_BASE_URL}, so pinning the localhost default here would fail on any machine that has
	 * the prod value exported.
	 */
	@Test
	void bindsALinkBaseTheEnvironmentCanOverride() {
		assertThat(properties.baseUrl()).startsWith("http");
	}

	/**
	 * The one misconfiguration worth failing startup over: a sender the provider has not verified is
	 * dropped silently, and an unset one is that with extra steps. Both are only ever noticed days
	 * later, on a background thread, by an email that never arrived.
	 */
	@Test
	void rejectsAnUnsetSenderAtStartup() {
		new ApplicationContextRunner()
				.withUserConfiguration(Config.class)
				.withPropertyValues("app.mail.from=", "app.mail.base-url=https://2doai.app")
				.run(context -> assertThat(context).hasFailed()
						.getFailure().hasStackTraceContaining("from"));
	}

	@Configuration
	@EnableConfigurationProperties(MailboxProperties.class)
	static class Config {
	}
}
