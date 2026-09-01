package com.thedariusz.todoai.mail;

import jakarta.validation.constraints.NotBlank;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

/**
 * The two things about an outgoing message that are the app's business rather than the transport's.
 * Everything else — host, port, credentials, STARTTLS — lives under {@code spring.mail.*} and is
 * consumed by Boot's own auto-configuration, exactly as {@code LlmProperties} carries only the model
 * slugs while {@code spring.ai.openai.*} carries the connection.
 *
 * <p>Both are validated for the same reason the rhythm's numbers are: they are read on a background
 * thread days after boot, so a missing value would surface as an exception in a log nobody is
 * watching rather than as a startup failure.
 *
 * @param from the {@code From:} header, sender name included — must be an address on a domain
 *        verified with the provider, or every message is silently dropped
 * @param baseUrl where the app lives, so the link in an email opens the app the reader actually uses
 *        (a localhost default keeps dev honest; prod sets {@code APP_BASE_URL})
 */
@ConfigurationProperties(prefix = "app.mail")
@Validated
public record MailProperties(@NotBlank String from, @NotBlank String baseUrl) {
}
