package com.thedariusz.todoai.goal;

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
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import com.thedariusz.todoai.category.LifeDomain;
import com.thedariusz.todoai.security.UserOwned;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;

/**
 * A long-term goal or a someday dream (S-02, FR-004/FR-005) — one aggregate for both layers,
 * discriminated by {@link GoalLayer}, because everything downstream (S-04 proposals, S-08 views,
 * S-09 auto-tag) consumes the union and only the horizon differs.
 *
 * <p><b>The invariant</b>: a {@code GOAL} has a {@link GoalHorizon}, a {@code DREAM} has none.
 * It is enforced at three depths — the request DTOs ({@code @AssertTrue} → 422), this constructor
 * and {@link #update} ({@code IllegalArgumentException}, unreachable through the API but binding on
 * every future caller), and the {@code chk_goal_layer_horizon} CHECK constraint in {@code V6}.
 *
 * <p>Completion is modelled as a nullable timestamp rather than a boolean: {@code completedAt} is
 * both the state (null = active) and the moment S-03's memory enrichment will read.
 *
 * <p>The first {@code @Enumerated} mapping in the project, and the first entity FK to
 * {@code category.code} — mapping the category as a {@link LifeDomain} rather than a string is safe
 * precisely because {@code CategorySyncCheck} pins the enum names to the table's codes at startup.
 */
@Entity
@Table(name = "goal")
public class Goal implements UserOwned {

	/**
	 * Referenced by the {@code @Size}/{@code @Column} below, so those cannot drift — but copied by
	 * hand into {@code goal.content}'s width in {@code V6}, the spec's {@code GoalContent.maxLength}
	 * and the SPA's {@code maxLength}, which can. {@code ddl-auto=validate} does not catch the
	 * migration copy (it ignores column length), so
	 * {@code GoalApiTest.publishesTheWireEnumsTheContractAnchors} holds all four together.
	 */
	public static final int MAX_CONTENT_LENGTH = 500;

	/** Stated once: both request DTOs use it as their {@code @AssertTrue} message. */
	static final String HORIZON_RULE = "a GOAL requires a horizon and a DREAM forbids one";

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	@NotBlank
	@Size(max = MAX_CONTENT_LENGTH)
	@Column(nullable = false, length = MAX_CONTENT_LENGTH)
	private String content;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private GoalLayer layer;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private GoalHorizon horizon;

	@Enumerated(EnumType.STRING)
	@Column(name = "category_code", length = 32)
	private LifeDomain category;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Goal() {
		// JPA requires a no-arg constructor.
	}

	public Goal(UUID userId, String content, GoalLayer layer, GoalHorizon horizon, LifeDomain category) {
		this.userId = Objects.requireNonNull(userId, "userId");
		apply(content, layer, horizon, category);
	}

	/**
	 * Full-replace edit — the single write path behind {@code PUT /api/goals/{id}}, so it also covers
	 * the dream ↔ goal conversion and therefore has to re-check the invariant.
	 */
	public void update(String content, GoalLayer layer, GoalHorizon horizon, LifeDomain category) {
		apply(content, layer, horizon, category);
	}

	/**
	 * Mark done at the given moment. Truly idempotent: an entry that is already done keeps its
	 * original moment, because {@link #update} re-asserts completion on every full-replace PUT and
	 * the SPA sends the entry's own state back with each edit — re-stamping here would move the date
	 * every time someone fixes a typo, and the moment S-03 reads is unrecoverable once overwritten.
	 * Going back through {@link #reopen} and completing again is the only way to set a new one.
	 */
	public void complete(OffsetDateTime at) {
		Objects.requireNonNull(at, "at");
		if (this.completedAt == null) {
			this.completedAt = at;
		}
	}

	/** Back to active. No delete exists in S-02 — un-completing is the only way back. */
	public void reopen() {
		this.completedAt = null;
	}

	/**
	 * The layer × horizon rule, stated once in Java so the aggregate and both request DTOs cannot
	 * drift apart. A null layer passes here — {@code @NotNull} reports that as its own violation
	 * rather than letting this rule blame the horizon for it.
	 */
	static boolean hasConsistentHorizon(GoalLayer layer, GoalHorizon horizon) {
		return layer == null || (layer == GoalLayer.GOAL) == (horizon != null);
	}

	private void apply(String content, GoalLayer layer, GoalHorizon horizon, LifeDomain category) {
		this.content = Objects.requireNonNull(content, "content");
		this.layer = Objects.requireNonNull(layer, "layer");
		if (!hasConsistentHorizon(layer, horizon)) {
			throw new IllegalArgumentException(HORIZON_RULE + ", got " + layer
					+ " with horizon " + horizon);
		}
		this.horizon = horizon;
		this.category = category;
	}

	public UUID getId() {
		return id;
	}

	@Override
	public UUID getUserId() {
		return userId;
	}

	public String getContent() {
		return content;
	}

	public GoalLayer getLayer() {
		return layer;
	}

	public GoalHorizon getHorizon() {
		return horizon;
	}

	public LifeDomain getCategory() {
		return category;
	}

	public OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
