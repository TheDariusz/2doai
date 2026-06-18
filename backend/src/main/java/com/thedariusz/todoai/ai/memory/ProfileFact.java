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

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A durable, typed fact in the semantic profile of an {@link AiMemory} (e.g.
 * {@code kind="occupation", content="Software engineer"}). Created via
 * {@link AiMemory#addFact}, never standalone — it is part of the aggregate, not its own
 * root.
 */
@Entity
@Table(name = "ai_memory_profile_fact")
public class ProfileFact {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ai_memory_id", nullable = false, updatable = false)
	private AiMemory memory;

	@Column(nullable = false)
	private String kind;

	@Column(nullable = false)
	private String content;

	@Column
	private String provenance;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected ProfileFact() {
		// JPA requires a no-arg constructor.
	}

	ProfileFact(AiMemory memory, String kind, String content, String provenance) {
		this.memory = memory;
		this.kind = kind;
		this.content = content;
		this.provenance = provenance;
	}

	// Identity-based equals/hashCode: a constant hashCode keeps Set membership stable across
	// the transient → persistent transition (the v7 id is null until persist), and equals
	// compares ids once assigned. The canonical JPA-entity-in-a-Set pattern.
	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		return o instanceof ProfileFact other && id != null && id.equals(other.id);
	}

	@Override
	public int hashCode() {
		return ProfileFact.class.hashCode();
	}

	public UUID getId() {
		return id;
	}

	public String getKind() {
		return kind;
	}

	public String getContent() {
		return content;
	}

	public String getProvenance() {
		return provenance;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
