package com.thedariusz.todoai.reminder;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Binds {@link ReminderProperties}, as {@code ProposalConfig} binds the rhythm's. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(ReminderProperties.class)
class ReminderConfig {
}
