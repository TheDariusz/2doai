package com.thedariusz.todoai.ai;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the LLM gateway. Connection settings (base URL, key, timeout,
 * retries) live under {@code spring.ai.openai.*} and are consumed by Spring AI's
 * auto-configured OpenAI client; this record carries only what stays a domain concern: the
 * model slugs callers pick per request.
 *
 * @param model model slugs per use-case (OpenRouter slugs use dots, not dashes)
 */
@ConfigurationProperties(prefix = "llm")
public record LlmProperties(Model model) {

	/** Model slugs split by use-case (Haiku = auto-tag/S-09, Sonnet = proposals/S-04). */
	public record Model(String haiku, String sonnet) {
	}
}
