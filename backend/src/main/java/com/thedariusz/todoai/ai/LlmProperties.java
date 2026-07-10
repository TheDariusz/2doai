package com.thedariusz.todoai.ai;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * Typed configuration for the LLM gateway. Connection settings (base URL, key, timeout,
 * retries) live under {@code spring.ai.openai.*} and are consumed by Spring AI's
 * auto-configured OpenAI client; this record carries only what stays a domain concern: the
 * model slugs callers pick per request. Both slugs are required — {@code @Validated} rejects a
 * blank value at startup rather than on the first call.
 *
 * @param model model slugs per use-case (OpenRouter slugs use dots, not dashes)
 */
@ConfigurationProperties(prefix = "llm")
@Validated
public record LlmProperties(@Valid Model model) {

	/** Model slugs split by use-case (Haiku = auto-tag/S-09, Sonnet = proposals/S-04). */
	public record Model(@NotBlank String haiku, @NotBlank String sonnet) {
	}
}
