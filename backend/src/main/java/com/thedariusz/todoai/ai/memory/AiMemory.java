package com.thedariusz.todoai.ai.memory;

import java.time.OffsetDateTime;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.UUID;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * Root of the per-user AI-memory aggregate (F-02) — the first domain aggregate in the
 * project, hence the first use of the UUIDv7 surrogate-PK + {@code timestamptz}
 * audit-column conventions (see {@code data-model.md}). It owns a <em>semantic profile</em>
 * ({@link ProfileFact} — durable typed facts) and a bounded <em>episodic log</em>
 * ({@link Episode} — completions, proposal outcomes; never deleted, capped only at render
 * time).
 *
 * <p>Children are created through intent-revealing methods ({@link #addFact},
 * {@link #recordEpisode}) rather than exposed setters, so the bidirectional link is always
 * consistent and the aggregate stays the consistency boundary. No write paths run in F-02;
 * S-03 (enrichment) and S-04 (proposal outcomes) are the first callers.
 *
 * <p>{@code user_id} is a plain unique UUID column today — the FK to {@code user(id)} is
 * deferred to S-01 (documented inline in {@code V3}).
 */
@Entity
@Table(name = "ai_memory")
public class AiMemory {

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@Column(name = "user_id", nullable = false, updatable = false, unique = true)
	private UUID userId;

	// Sets (not Lists) so both child collections can be join-fetched in one query without
	// Hibernate's MultipleBagFetchException; ordering is a render-time concern (AiMemoryRenderer
	// sorts episodes by occurred_at), so insertion order here is incidental.
	@OneToMany(mappedBy = "memory", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<ProfileFact> profileFacts = new LinkedHashSet<>();

	@OneToMany(mappedBy = "memory", cascade = CascadeType.ALL, orphanRemoval = true)
	private Set<Episode> episodes = new LinkedHashSet<>();

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected AiMemory() {
		// JPA requires a no-arg constructor.
	}

	public AiMemory(UUID userId) {
		this.userId = userId;
	}

	/** Add a durable profile fact (used by S-03 onboarding/enrichment). */
	public void addFact(String kind, String content, String provenance) {
		profileFacts.add(new ProfileFact(this, kind, content, provenance));
	}

	/** Append an episode to the log (used by S-03/S-04). The log is never pruned. */
	public void recordEpisode(String eventType, String payload, OffsetDateTime occurredAt) {
		episodes.add(new Episode(this, eventType, payload, occurredAt));
	}

	public UUID getId() {
		return id;
	}

	public UUID getUserId() {
		return userId;
	}

	public Set<ProfileFact> getProfileFacts() {
		return Collections.unmodifiableSet(profileFacts);
	}

	public Set<Episode> getEpisodes() {
		return Collections.unmodifiableSet(episodes);
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
