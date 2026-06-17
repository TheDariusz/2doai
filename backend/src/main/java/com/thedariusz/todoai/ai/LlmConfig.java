package com.thedariusz.todoai.ai;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * LLM wiring seam. Transport, auth, timeouts and retry are owned by Spring AI's
 * auto-configured OpenAI client (driven by {@code spring.ai.openai.*}), so the only thing left
 * to enable here is {@link LlmProperties} binding for the model slugs the {@link SpringAiLlmClient}
 * caller passes per request.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(LlmProperties.class)
class LlmConfig {
}
