package com.thedariusz.todoai.security;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * One remembered browser: what lets a user back into a session after the in-memory one is gone (a
 * browser restart, a backend deploy). The cookie carries {@link #series} and {@link #tokenValue}; the
 * series finds the row and the value must match it.
 *
 * <p><b>Not a session.</b> Nothing here is read while a session is alive — only on the one request
 * per device that arrives without one — so the database is not on the path of an ordinary request and
 * Neon can still suspend (see {@code context/foundation/auth-session-model.md}, addendum 2026-11-14).
 *
 * <p>The value is fixed for the row's whole life; only {@link #lastUsedAt} moves. Why it must not
 * rotate is told on {@link StableTokenRememberMeServices}.
 */
@Entity
@Table(name = "remember_me_token")
public class RememberMeToken implements UserOwned {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@Column(nullable = false, updatable = false, length = 64)
	private String series;

	@Column(name = "token_value", nullable = false, length = 64)
	private String tokenValue;

	@Column(name = "last_used_at", nullable = false)
	private OffsetDateTime lastUsedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected RememberMeToken() {
		// JPA requires a no-arg constructor.
	}

	public RememberMeToken(UUID userId, String series, String tokenValue, OffsetDateTime issuedAt) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.series = Objects.requireNonNull(series, "series");
		this.tokenValue = Objects.requireNonNull(tokenValue, "tokenValue");
		this.lastUsedAt = Objects.requireNonNull(issuedAt, "issuedAt");
	}

	@Override
	public UUID getUserId() {
		return userId;
	}

	/** Never renders the value: half of a credential has no business in a log line. */
	@Override
	public String toString() {
		return "RememberMeToken[id=" + id + ", userId=" + userId + "]";
	}
}
