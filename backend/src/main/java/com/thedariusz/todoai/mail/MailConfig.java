package com.thedariusz.todoai.mail;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/**
 * Mail wiring seam. Mirrors {@code MemoryConfig} and {@code ProposalConfig}: the only thing to enable
 * is {@link MailboxProperties} binding, so a typo in a key fails the build rather than shipping a sender
 * address nobody verified.
 */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(MailboxProperties.class)
class MailConfig {
}
