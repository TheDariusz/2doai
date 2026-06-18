package com.thedariusz.todoai.ai.memory;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * AI-memory wiring seam. Mirrors {@code LlmConfig}: the only thing to enable is
 * {@link MemoryProperties} binding (the render cap consumed by {@link AiMemoryRenderer}).
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MemoryProperties.class)
class MemoryConfig {
}
