package com.thedariusz.todoai.ai;

import java.util.List;
import java.util.Map;

import com.thedariusz.todoai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Live round-trip against the <em>real</em> OpenRouter endpoint, exercising the prod wiring:
 * the Spring AI auto-configured {@code ChatModel} (pointed at OpenRouter via
 * {@code spring.ai.openai.*}) behind the {@link LlmClient} port, with the no-training
 * {@code provider} routing on every request and a strict {@code json_schema} structured call.
 *
 * <p><strong>Gated.</strong> The whole class is disabled unless {@code OPENROUTER_API_KEY} is
 * present, so CI stays hermetic and green (the gate short-circuits before any Spring context or
 * Testcontainers boot). It runs only when a developer supplies a real key — the Phase-4
 * human-in-the-loop live verification:
 * <pre>{@code OPENROUTER_API_KEY=… mvn test -Dtest=OpenRouterLiveTest}</pre>
 * (Docker must be running — the full-context boot brings up the Testcontainers Postgres, same as
 * the other integration tests.)
 *
 * <p>Both tests target <strong>Sonnet</strong> only — proving {@code json_schema strict} on
 * Sonnet ({@code ai-provider.md} item b-on-Sonnet). Haiku's strict support and the Polish A/B
 * (items b-on-Haiku, c) are deferred to S-09. Prompts are intentionally tiny to keep token use
 * (and credit cost) negligible.
 */
@EnabledIfEnvironmentVariable(named = "OPENROUTER_API_KEY", matches = ".+")
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class OpenRouterLiveTest {

	@Autowired
	LlmClient llmClient;

	@Autowired
	LlmProperties properties;

	@Test
	void freeTextRoundTripOnSonnet() {
		String reply = llmClient.complete(LlmRequest.of(
				properties.model().sonnet(),
				LlmMessage.system("Reply with a single lowercase word and nothing else."),
				LlmMessage.user("Respond with the word: pong")));

		assertThat(reply).isNotBlank();
	}

	@Test
	void structuredRoundTripOnSonnet() {
		// Strict json_schema (OpenAI strict-mode rules: object, additionalProperties:false, every
		// property required) — Spring AI forces strict=true and OpenRouter forwards it to Anthropic.
		JsonSchema schema = new JsonSchema("ping_reply", Map.of(
				"type", "object",
				"additionalProperties", false,
				"properties", Map.of("reply", Map.of("type", "string")),
				"required", List.of("reply")));

		Reply result = llmClient.completeStructured(
				LlmRequest.of(
						properties.model().sonnet(),
						LlmMessage.user("Return JSON whose \"reply\" field is the word pong.")),
				Reply.class,
				schema);

		assertThat(result).isNotNull();
		assertThat(result.reply()).isNotBlank();
	}

	/** Target type for the structured-output round-trip. */
	record Reply(String reply) {
	}
}
