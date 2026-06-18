package com.thedariusz.todoai.ai.memory;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves {@link MemoryProperties} binds the render cap from the committed
 * {@code application.properties} (relaxed binding of {@code ai.memory.render.max-episodes} →
 * {@code render().maxEpisodes()}) — so a typo in the property key fails the build rather than
 * silently shipping. Mirrors {@code LlmPropertiesTest}; no auto-configuration runs, so no
 * datasource is needed.
 */
@SpringJUnitConfig(MemoryPropertiesTest.Config.class)
@TestPropertySource(locations = "classpath:application.properties")
class MemoryPropertiesTest {

	@Autowired
	MemoryProperties properties;

	@Test
	void bindsRenderCapFromApplicationProperties() {
		assertThat(properties.render().maxEpisodes()).isEqualTo(20);
	}

	@Configuration
	@EnableConfigurationProperties(MemoryProperties.class)
	static class Config {
	}
}
