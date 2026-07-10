package com.thedariusz.todoai.ai.memory;

import java.time.OffsetDateTime;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * One entry in the episodic log of an {@link AiMemory} — a completion, proposal outcome or
 * similar event. {@code eventType} is a generic discriminator and {@code payload} is an
 * opaque {@code jsonb} document (stored as a raw JSON {@link String}, so the mapping carries
 * no Jackson coupling). Episodes are append-only: {@code occurred_at} is the domain time of
 * the event and there is no {@code updated_at} (a row, once written, never changes). The
 * "last N" cap is applied at render time, never by deleting rows.
 *
 * <p>Created via {@link AiMemory#recordEpisode}, never standalone.
 */
@Entity
@Table(name = "ai_memory_episode")
public class Episode {

	/** Mirrors the {@code ai_memory_episode.event_type VARCHAR(64)} column width. */
	static final int MAX_EVENT_TYPE_LENGTH = 64;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ai_memory_id", nullable = false, updatable = false)
	private AiMemory memory;

	@NotBlank
	@Size(max = MAX_EVENT_TYPE_LENGTH)
	@Column(name = "event_type", nullable = false, length = MAX_EVENT_TYPE_LENGTH)
	private String eventType;

	@NotBlank
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(nullable = false)
	private String payload;

	@NotNull
	@Column(name = "occurred_at", nullable = false)
	private OffsetDateTime occurredAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	protected Episode() {
		// JPA requires a no-arg constructor.
	}

	Episode(AiMemory memory, String eventType, String payload, OffsetDateTime occurredAt) {
		this.memory = memory;
		this.eventType = eventType;
		this.payload = payload;
		this.occurredAt = occurredAt;
	}

	// Identity-based equals/hashCode (see ProfileFact for the rationale): constant hashCode +
	// id-based equals so the Set stays consistent across the transient → persistent transition.
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof Episode other && id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return Episode.class.hashCode();
	}

	public UUID getId() {
		return id;
	}

	public String getEventType() {
		return eventType;
	}

	public String getPayload() {
		return payload;
	}

	public OffsetDateTime getOccurredAt() {
		return occurredAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}
}
