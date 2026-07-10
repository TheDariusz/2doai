package com.thedariusz.todoai.ai;

import ch.qos.logback.classic.Level;
import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;

import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.slf4j.LoggerFactory;
import org.springframework.ai.chat.messages.AssistantMessage;
import org.springframework.ai.chat.messages.MessageType;
import org.springframework.ai.chat.model.ChatModel;
import org.springframework.ai.chat.model.ChatResponse;
import org.springframework.ai.chat.model.Generation;
import org.springframework.ai.chat.prompt.Prompt;
import org.springframework.ai.openai.OpenAiChatModel;
import org.springframework.ai.openai.OpenAiChatOptions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Hermetic unit tests for {@link SpringAiLlmClient} against a mocked {@link ChatModel}.
 *
 * <p>The adapter owns request <em>shaping</em> only — transport, retry and timeouts moved into
 * Spring AI's OpenAI client (configured in {@code application.properties}, not unit-tested
 * here). So these tests assert at the seam the adapter controls: the {@link OpenAiChatOptions}
 * and messages handed to {@code ChatModel.call(...)} — the no-training routing on every call,
 * the strict {@code json_schema} response format, role mapping — plus typed deserialization
 * and error translation to {@link LlmException}. The {@code extraBody} → wire nesting and the
 * 429/5xx retry semantics are the OpenAI client's contract, verified by the deferred live
 * round-trip (Phase 4).
 */
class SpringAiLlmClientTest {

	private static final String SONNET = "anthropic/claude-sonnet-4.6";
	private static final String HAIKU = "anthropic/claude-haiku-4.5";

	private ChatModel chatModel;
	private SpringAiLlmClient client;

	@BeforeEach
	void setUp() {
		this.chatModel = mock(ChatModel.class);
		this.client = new SpringAiLlmClient(chatModel);
	}

	private void stubContent(String content) {
		ChatResponse response = new ChatResponse(List.of(new Generation(new AssistantMessage(content))));
		when(chatModel.call(any(Prompt.class))).thenReturn(response);
	}

	private Prompt capturePrompt() {
		ArgumentCaptor<Prompt> captor = ArgumentCaptor.forClass(Prompt.class);
		org.mockito.Mockito.verify(chatModel).call(captor.capture());
		return captor.getValue();
	}

	private static OpenAiChatOptions optionsOf(Prompt prompt) {
		return (OpenAiChatOptions) prompt.getOptions();
	}

	@Test
	void completeReturnsAssistantTextAndSendsModelAndUserMessage() {
		stubContent("Hello there");

		String result = client.complete(LlmRequest.of(SONNET, LlmMessage.user("Hi")));

		assertThat(result).isEqualTo("Hello there");
		Prompt prompt = capturePrompt();
		assertThat(optionsOf(prompt).getModel()).isEqualTo(SONNET);
		assertThat(prompt.getInstructions()).singleElement().satisfies(message -> {
			assertThat(message.getMessageType()).isEqualTo(MessageType.USER);
			assertThat(message.getText()).isEqualTo("Hi");
		});
	}

	@Test
	void everyFreeTextRequestCarriesNoTrainingProviderRouting() {
		stubContent("ok");

		client.complete(LlmRequest.of(SONNET, LlmMessage.user("hi")));

		assertThat(optionsOf(capturePrompt()).getExtraBody())
				.isEqualTo(Map.of("provider", Map.of("data_collection", "deny")));
	}

	@Test
	void mapsRolesToSpringMessageTypes() {
		stubContent("ok");

		client.complete(LlmRequest.of(SONNET,
				LlmMessage.system("be brief"),
				LlmMessage.user("hi"),
				LlmMessage.assistant("hello")));

		assertThat(capturePrompt().getInstructions())
				.extracting(message -> message.getMessageType())
				.containsExactly(MessageType.SYSTEM, MessageType.USER, MessageType.ASSISTANT);
	}

	@Test
	void completeStructuredSendsStrictJsonSchemaAndDeserializes() {
		stubContent("{\"label\":\"WORK\"}");
		JsonSchema schema = new JsonSchema("category", Map.of(
				"type", "object",
				"properties", Map.of("label", Map.of("type", "string")),
				"required", List.of("label")));

		Tag result = client.completeStructured(
				LlmRequest.of(HAIKU, LlmMessage.user("classify")), Tag.class, schema);

		assertThat(result.label()).isEqualTo("WORK");
		OpenAiChatModel.ResponseFormat responseFormat = optionsOf(capturePrompt()).getResponseFormat();
		assertThat(responseFormat.getType()).isEqualTo(OpenAiChatModel.ResponseFormat.Type.JSON_SCHEMA);
		assertThat(responseFormat.getJsonSchema())
				.contains("\"type\":\"object\"")
				.contains("\"label\"");
	}

	@Test
	void structuredRequestAlsoCarriesNoTrainingProviderRouting() {
		stubContent("{\"label\":\"WORK\"}");
		JsonSchema schema = new JsonSchema("category", Map.of("type", "object"));

		client.completeStructured(LlmRequest.of(HAIKU, LlmMessage.user("classify")), Tag.class, schema);

		assertThat(optionsOf(capturePrompt()).getExtraBody())
				.isEqualTo(Map.of("provider", Map.of("data_collection", "deny")));
	}

	@Test
	void throwsLlmExceptionWhenStructuredResponseIsNotValidJson() {
		stubContent("not json");
		JsonSchema schema = new JsonSchema("category", Map.of("type", "object"));

		assertThatThrownBy(() -> client.completeStructured(
				LlmRequest.of(HAIKU, LlmMessage.user("classify")), Tag.class, schema))
				.isInstanceOf(LlmException.class);
	}

	@Test
	void throwsLlmExceptionWhenCompletionIsBlank() {
		// A truncation/content-filter stop can yield a present-but-blank completion; it must
		// surface as a failure, not pass through as a successful (empty) answer.
		stubContent("   ");

		assertThatThrownBy(() -> client.complete(LlmRequest.of(SONNET, LlmMessage.user("hi"))))
				.isInstanceOf(LlmException.class);
	}

	@Test
	void translatesModelFailureToLlmException() {
		when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

		assertThatThrownBy(() -> client.complete(LlmRequest.of(SONNET, LlmMessage.user("hi"))))
				.isInstanceOf(LlmException.class);
	}

	@Test
	void logsWarningWithModelAndCauseWhenModelCallFails() {
		ListAppender<ILoggingEvent> appender = new ListAppender<>();
		Logger clientLogger = (Logger) LoggerFactory.getLogger(SpringAiLlmClient.class);
		clientLogger.addAppender(appender);
		appender.start();
		when(chatModel.call(any(Prompt.class))).thenThrow(new RuntimeException("boom"));

		try {
			assertThatThrownBy(() -> client.complete(LlmRequest.of(SONNET, LlmMessage.user("hi"))))
					.isInstanceOf(LlmException.class);
		}
		finally {
			clientLogger.detachAppender(appender);
		}

		// Observability on the only outbound dependency: a WARN naming the model, with the
		// cause attached for debugging. The slug is not sensitive; content/key are never logged.
		assertThat(appender.list).anySatisfy(event -> {
			assertThat(event.getLevel()).isEqualTo(Level.WARN);
			assertThat(event.getFormattedMessage()).contains(SONNET);
			assertThat(event.getThrowableProxy()).isNotNull();
		});
	}

	/** Target type for the structured-output deserialization test. */
	record Tag(String label) {
	}
}
