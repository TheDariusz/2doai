package com.thedariusz.todoai.ai;

import java.util.List;
import java.util.Map;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.Message;
import org.springframework.ai.chat.messages.SystemMessage;
import org.springframework.ai.chat.messages.UserMessage;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;
import org.springframework.stereotype.Component;

/**
 * {@link LlmClient} implemented on Spring AI's {@link ChatModel} (auto-configured for
 * OpenRouter's OpenAI-compatible endpoint via {@code spring.ai.openai.*} — OpenRouter is the
 * swappable gateway per {@code ai-provider.md}, reached with the OpenAI client, not the
 * Anthropic SDK).
 *
 * <p>The adapter only shapes the request: model slug (caller-chosen, per {@link LlmRequest}),
 * the no-training {@code provider} routing block, and — for structured calls — the strict
 * {@code json_schema} response format. Transport, bounded retry (429/5xx, fail-fast on other
 * 4xx) and timeouts are owned by the underlying OpenAI client and configured in
 * {@code application.properties}; failures are translated into {@link LlmException}.
 *
 * <p>The in-code half of the PRD privacy guardrail lives in {@link #baseOptions}: every
 * request carries {@code provider: {data_collection: "deny"}} (Spring AI forwards
 * {@code extraBody} as nested OpenAI {@code additionalBodyProperties}), so OpenRouter routes
 * only to providers that do not retain request data for training.
 */
@Component
class SpringAiLlmClient implements LlmClient {

	/**
	 * No-training routing — the in-code half of the PRD privacy guardrail. Forwarded on
	 * every request (free-text and structured) as a top-level body field. Confirmed live in
	 * Phase 4.
	 */
	private static final Map<String, Object> NO_TRAINING_PROVIDER =
			Map.of("provider", Map.of("data_collection", "deny"));

	private final ChatModel chatModel;

	/**
	 * Mapper for the structured-output path only: schema map → JSON string (Spring AI's
	 * {@code ResponseFormat.jsonSchema} takes a string) and response content → the caller's
	 * type. Self-managed so the adapter doesn't couple to whichever {@code ObjectMapper} bean
	 * is primary on the classpath; the {@link ChatModel} handles HTTP (de)serialization itself.
	 */
	private final ObjectMapper objectMapper = new ObjectMapper();

	SpringAiLlmClient(ChatModel chatModel) {
		this.chatModel = chatModel;
	}

	@Override
	public String complete(LlmRequest request) {
		OpenAiChatOptions options = baseOptions(request.model()).build();
		return call(request, options);
	}

	@Override
	public <T> T completeStructured(LlmRequest request, Class<T> type, JsonSchema schema) {
		// Spring AI emits json_schema with strict=true; the caller's schema name is cosmetic
		// (Spring AI labels it "json_schema" on the wire) so it isn't forwarded here.
		var responseFormat = OpenAiChatModel.ResponseFormat.builder()
				.type(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA)
				.jsonSchema(serializeSchema(schema))
				.build();
		OpenAiChatOptions options = baseOptions(request.model())
				.responseFormat(responseFormat)
				.build();
		return deserialize(call(request, options), type);
	}

	private String serializeSchema(JsonSchema schema) {
		try {
			return objectMapper.writeValueAsString(schema.schema());
		}
		catch (JsonProcessingException ex) {
			throw new LlmException("Failed to serialize JSON schema " + schema.name(), ex);
		}
	}

	private <T> T deserialize(String content, Class<T> type) {
		try {
			return objectMapper.readValue(content, type);
		}
		catch (JsonProcessingException ex) {
			throw new LlmException(
					"Failed to deserialize structured LLM response into " + type.getSimpleName(), ex);
		}
	}

	private OpenAiChatOptions.Builder baseOptions(String model) {
		return OpenAiChatOptions.builder()
				.model(model)
				.extraBody(NO_TRAINING_PROVIDER);
	}

	private String call(LlmRequest request, OpenAiChatOptions options) {
		List<Message> messages = request.messages().stream()
				.map(SpringAiLlmClient::toSpringMessage)
				.toList();
		ChatResponse response;
		try {
			response = chatModel.call(new Prompt(messages, options));
		}
		catch (RuntimeException ex) {
			// Transport/provider failure surfaced by Spring AI (after the OpenAI client's own
			// retries) — collapse to the port's single failure type.
			throw new LlmException("LLM request failed", ex);
		}
		return extractContent(response);
	}

	private static Message toSpringMessage(LlmMessage message) {
		return switch (message.role()) {
			case SYSTEM -> new SystemMessage(message.content());
			case USER -> new UserMessage(message.content());
			case ASSISTANT -> new AssistantMessage(message.content());
		};
	}

	private String extractContent(ChatResponse response) {
		var result = response == null ? null : response.getResult();
		var output = result == null ? null : result.getOutput();
		String text = output == null ? null : output.getText();
		if (text == null) {
			throw new LlmException("LLM returned no content");
		}
		return text;
	}
}
