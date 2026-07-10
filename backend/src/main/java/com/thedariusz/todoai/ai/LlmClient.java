package com.thedariusz.todoai.ai;

/**
 * Gateway the rest of the app depends on to talk to a large-language model.
 *
 * <p>The provider is an implementation detail behind this port (clean-architecture
 * preference): call sites pass a model slug per request (the model split is a caller
 * concern) and never see HTTP, Spring, or provider-specific types. The single failure
 * mode callers handle is {@link LlmException}.
 *
 * <p>Two operations:
 * <ul>
 *   <li>{@link #complete(LlmRequest)} — free-text completion.</li>
 *   <li>{@link #completeStructured(LlmRequest, Class, JsonSchema)} — schema-constrained
 *       JSON, deserialized into a typed object.</li>
 * </ul>
 */
public interface LlmClient {

	/**
	 * Free-text completion. Returns the assistant's text content.
	 *
	 * @throws LlmException if the request fails (transport error, non-2xx response after
	 *                      retries, or a malformed response).
	 */
	String complete(LlmRequest request);

	/**
	 * Schema-constrained completion. The provider is asked to emit strict JSON matching
	 * {@code schema}; the JSON is deserialized into {@code type}.
	 *
	 * @throws LlmException if the request fails or the response cannot be deserialized
	 *                      into {@code type}.
	 */
	<T> T completeStructured(LlmRequest request, Class<T> type, JsonSchema schema);
}
