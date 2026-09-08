package com.thedariusz.todoai.user;

import java.time.OffsetDateTime;
import java.util.Objects;
import java.util.UUID;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
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
 * <p>Deliberately <em>minimal identity</em> (YAGNI): email + password hash + the language the
 * account reads in. Display name, flags,
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
	 * around deploys. Null until the scheduler has drawn a first moment (at boot, or on verification).
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

	/**
	 * The language every surface addressed to this person is rendered in (DEV-49, FR-002).
	 *
	 * <p><b>Nullable in the column, never null through {@link #getLanguage()}.</b> {@code V10} adds the
	 * column with no {@code DEFAULT} and rewrites no rows, so every account that existed before the
	 * choice was offered holds null — and null means "never chosen", which reads as Polish. That
	 * <em>is</em> the FR-004 backfill: those accounts keep reading Polish and were never asked
	 * anything. Note this is not {@link AppLanguage#DEFAULT}, and must not be collapsed into it: a
	 * request naming no language the app speaks means English, because the app is English-first; an
	 * account naming none means Polish, because it predates the question.
	 *
	 * <p><b>Read here, never written here</b> — the same rule, for the same reason, as
	 * {@link #nextProposalAt}: there is no setter, and {@code UserRepository.updateLanguage} moves the
	 * column with a targeted update that cannot resurrect a deleted account.
	 */
	@Enumerated(EnumType.STRING)
	@Column(name = "preferred_language", length = 2)
	private AppLanguage preferredLanguage;

	/**
	 * When the owner of this address proved they read it (DEV-51), and null until they do — so this
	 * one column is the whole answer to "may the app act on this account at all". An unverified
	 * account cannot log in, and the natural rhythm never writes to it: registration is open, but a
	 * stranger's mailbox is not a place the app is entitled to send anything but the code itself.
	 *
	 * <p>A moment rather than a flag because "when" answers "whether" as well, and additionally says
	 * how long an account sat unproved — which is what a cleanup of dead sign-ups would key on if one
	 * is ever wanted. {@code V13} backfills every account that predates the question, so they read as
	 * verified and were never asked anything.
	 *
	 * <p><b>Read here, never written here</b> — the rule the whole class follows, and here it is not
	 * only about resurrection: this is the column that decides whether the account is real, so it
	 * moves exclusively through {@code UserRepository.markEmailVerified}, one write with one caller.
	 */
	@Column(name = "email_verified_at")
	private OffsetDateTime emailVerifiedAt;

	/**
	 * The outstanding verification code, encoded with the same {@code PasswordEncoder} the password
	 * is — a 6-digit secret e-mailed in the clear is still a credential, and it is never stored in a
	 * form that could be read back out of a database dump. Cleared, together with
	 * {@link #verificationExpiresAt}, the moment the code is spent, so it cannot be replayed.
	 */
	@Column(name = "verification_code_hash")
	private String verificationCodeHash;

	/** When the outstanding code stops being accepted. Null exactly when there is no code. */
	@Column(name = "verification_expires_at")
	private OffsetDateTime verificationExpiresAt;

	/**
	 * Wrong guesses spent on the outstanding code — the cap that keeps a six-digit secret from being
	 * enumerable. Reset to zero whenever a new code is issued, so a locked-out address is one
	 * "send again" away from a fresh budget rather than dead forever.
	 */
	@Column(name = "verification_attempts", nullable = false)
	private int verificationAttempts;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected User() {
		// JPA requires a no-arg constructor.
	}

	public User(Email email, String passwordHash, AppLanguage language) {
		// Identity invariants fail fast at construction: no user without a valid email or a hash.
		// The declarative @NotBlank constraints keep the persist/schema-time guard in step.
		this.email = Objects.requireNonNull(email, "email").value();
		if (StringUtils.isBlank(passwordHash)) {
			throw new IllegalArgumentException("passwordHash must not be blank");
		}
		this.passwordHash = passwordHash;
		// FR-003 — a new account starts in the language its sign-up screen was shown in, so a language
		// is as much part of a new identity as the address. The null this field can still hold comes
		// only from a row written before the column existed, which JPA hydrates past this constructor.
		this.preferredLanguage = Objects.requireNonNull(language, "language");
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

	/** Never null: an account that never chose reads as Polish (FR-004) — see the field. */
	public AppLanguage getLanguage() {
		return preferredLanguage == null ? AppLanguage.PL : preferredLanguage;
	}

	public OffsetDateTime getNextProposalAt() {
		return nextProposalAt;
	}

	/** Whether the owner of this address has proved they read it — see {@link #emailVerifiedAt}. */
	public boolean isEmailVerified() {
		return emailVerifiedAt != null;
	}

	public OffsetDateTime getEmailVerifiedAt() {
		return emailVerifiedAt;
	}

	public String getVerificationCodeHash() {
		return verificationCodeHash;
	}

	public OffsetDateTime getVerificationExpiresAt() {
		return verificationExpiresAt;
	}

	public int getVerificationAttempts() {
		return verificationAttempts;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
