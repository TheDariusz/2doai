package com.thedariusz.todoai.proposal;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Proposal wiring seam. Mirrors {@code MemoryConfig}: the only thing to enable is
 * {@link RhythmProperties} binding, so a typo in a rhythm key fails the build rather than shipping a
 * schedule nobody drew.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(RhythmProperties.class)
class ProposalConfig {
}
