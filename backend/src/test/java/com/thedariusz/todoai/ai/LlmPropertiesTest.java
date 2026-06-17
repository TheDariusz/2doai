package com.thedariusz.todoai.ai;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link LlmProperties} binds the per-use-case model slugs from the committed
 * {@code application.properties} (relaxed binding of {@code llm.model.haiku} →
 * {@code model().haiku()}). Connection settings now live under {@code spring.ai.openai.*}
 * (Spring AI's own properties), so they're no longer asserted here. No auto-configuration
 * runs, so no datasource is needed.
 */
@SpringJUnitConfig(LlmPropertiesTest.Config.class)
@TestPropertySource(locations = "classpath:application.properties")
class LlmPropertiesTest {

	@Autowired
	LlmProperties properties;

	@Test
	void bindsModelSlugsFromApplicationProperties() {
		assertThat(properties.model().haiku()).isEqualTo("anthropic/claude-haiku-4.5");
		assertThat(properties.model().sonnet()).isEqualTo("anthropic/claude-sonnet-4.6");
	}

	@Configuration
	@EnableConfigurationProperties(LlmProperties.class)
	static class Config {
	}
}
