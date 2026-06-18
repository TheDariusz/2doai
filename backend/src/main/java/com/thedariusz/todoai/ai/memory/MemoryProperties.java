package com.thedariusz.todoai.ai.memory;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Typed configuration for the AI-memory mechanism. Today it carries only the render cap; the
 * episodic log itself is never pruned (rows are append-only), so this bound applies purely at
 * render time — {@link AiMemoryRenderer} injects only the last N episodes into a prompt/export
 * block, keeping prompt-token cost independent of how large the log grows.
 *
 * @param render render-time settings
 */
@ConfigurationProperties(prefix = "ai.memory")
public record MemoryProperties(Render render) {

	/**
	 * @param maxEpisodes how many of the most-recent episodes the renderer includes by default
	 */
	public record Render(int maxEpisodes) {
	}
}
