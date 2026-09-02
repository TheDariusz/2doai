package com.thedariusz.todoai.user;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import org.apache.commons.lang3.StringUtils;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * User identity aggregate (S-01, account-and-auth) — the first per-user boundary in the app and
 * the owner every later per-user aggregate ({@code ai_memory} today, goals/dreams/tasks next)
 * points its {@code user_id} FK at. Mirrors the {@code AiMemory} conventions: UUID v7 surrogate
 * PK, {@code timestamptz} audit columns, invariant-in-constructor, getters only.
 *
 * <p>Deliberately <em>minimal identity</em> (YAGNI): email + password hash. Display name, flags,
 * preferences are later, expand-only additions. The <b>raw password never enters the domain</b> —
 * the constructor takes an already-encoded hash (the {@code PasswordEncoder} runs in the
 * application layer), so the aggregate never sees or stores a plaintext credential.
 *
 * <p>Mapped to {@code app_user}, not {@code user}: {@code user} is a reserved word in Postgres
 * (see {@code V4__create_app_user.sql}).
 */
@Entity
@Table(name = "app_user")
public class User {

	/** Mirrors the {@code app_user.email VARCHAR(320)} column width (RFC 5321 max address length). */
	static final int MAX_EMAIL_LENGTH = 320;

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	private UUID id;

	// Stores the normalized (lowercased) email from the Email VO. unique = true documents the
	// app_user.email UNIQUE constraint (the login-lookup index) and keeps the mapping in step
	// with the schema Hibernate validates against.
	@NotBlank
	@Size(max = MAX_EMAIL_LENGTH)
	@Column(nullable = false, unique = true, length = MAX_EMAIL_LENGTH)
	private String email;

	@NotBlank
	@Column(name = "password_hash", nullable = false)
	private String passwordHash;

	/**
	 * When the natural rhythm next returns to this user (S-05, FR-011) — the only piece of the
	 * schedule that outlives the JVM, so a restart resumes the rhythm instead of bunching proposals
	 * around deploys. Null until the scheduler has drawn a first moment (at boot, or on registration).
	 *
	 * <p>Timing rather than identity, on the identity aggregate: the cheapest thing that works while
	 * the rhythm is the only foreign timing state here — a proposal-owned table is the upgrade the
	 * moment something else wants a column. It stays a plain value with no invariant of its own —
	 * {@code ProposalRhythm} decides what a legal next moment is.
	 *
	 * <p><b>Read here, never written here.</b> There is deliberately no setter: the rhythm moves this
	 * column by {@code UserRepository.scheduleNextProposalAt}, a targeted update, because a fire holds
	 * its account detached across a model call and saving one back would re-insert an account deleted
	 * in the meantime. Leaving a mutator would leave that bug one {@code save} away.
	 */
	@Column(name = "next_proposal_at")
	private OffsetDateTime nextProposalAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected User() {
		// JPA requires a no-arg constructor.
	}

	public User(Email email, String passwordHash) {
		// Identity invariants fail fast at construction: no user without a valid email or a hash.
		// The declarative @NotBlank constraints keep the persist/schema-time guard in step.
		this.email = Objects.requireNonNull(email, "email").value();
		if (StringUtils.isBlank(passwordHash)) {
			throw new IllegalArgumentException("passwordHash must not be blank");
		}
		this.passwordHash = passwordHash;
	}

	public UUID getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public OffsetDateTime getNextProposalAt() {
		return nextProposalAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
