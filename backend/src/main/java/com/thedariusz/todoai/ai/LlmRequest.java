package com.thedariusz.todoai.ai;

import java.util.List;

/**
 * A model-agnostic completion request: the model slug to call plus the conversation.
 * The model is passed per request because the model split (Haiku for auto-tag, Sonnet
 * for proposals) is a caller concern, not the gateway's.
 *
 * <p>Kept intentionally minimal for the F-02 foundation (model + messages). Optional
 * generation params (temperature, max-tokens, …) can be added here when a slice needs
 * them, without touching the {@link LlmClient} contract.
 */
public record LlmRequest(String model, List<LlmMessage> messages) {

	public LlmRequest {
		messages = List.copyOf(messages);
	}

	public static LlmRequest of(String model, LlmMessage... messages) {
		return new LlmRequest(model, List.of(messages));
	}
}
