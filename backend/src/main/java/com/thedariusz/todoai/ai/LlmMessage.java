package com.thedariusz.todoai.ai;

/**
 * One message in an {@link LlmRequest} conversation. Maps to an OpenAI-compatible
 * {@code {role, content}} pair at the wire boundary, but exposes no provider types.
 */
public record LlmMessage(Role role, String content) {

	public enum Role {
		SYSTEM, USER, ASSISTANT
	}

	public static LlmMessage system(String content) {
		return new LlmMessage(Role.SYSTEM, content);
	}

	public static LlmMessage user(String content) {
		return new LlmMessage(Role.USER, content);
	}

	public static LlmMessage assistant(String content) {
		return new LlmMessage(Role.ASSISTANT, content);
	}
}
