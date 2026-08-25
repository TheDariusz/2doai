package com.thedariusz.todoai.goal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;
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
 * A current task, a long-term goal or a someday dream (S-02/S-07, FR-003/FR-004/FR-005) — one
 * aggregate for all three layers, discriminated by {@link GoalLayer}, because everything downstream
 * (S-04 proposals, S-08 views, S-09 auto-tag, S-10 offline) consumes the union and only the time
 * fields differ. S-07 widened this rather than adding a parallel {@code Task}: splitting the
 * aggregate is worth it when tasks get a different lifecycle (recurrence, overdue alarms), and then
 * it is a migration rather than a rewrite.
 *
 * <p><b>The invariant</b>, one rule over two nullable time fields:
 *
 * <table><caption>layer × time fields</caption>
 * <tr><th>layer</th><th>{@code horizon}</th><th>{@code dueDate}</th></tr>
 * <tr><td>{@code GOAL}</td><td>required</td><td>forbidden</td></tr>
 * <tr><td>{@code DREAM}</td><td>forbidden</td><td>forbidden</td></tr>
 * <tr><td>{@code TASK}</td><td>forbidden</td><td>optional</td></tr>
 * </table>
 *
 * <p>It is enforced at three depths — the request DTOs ({@code @AssertTrue} → 422), {@link #update}
 * ({@code IllegalArgumentException}, unreachable through the API but binding on every future
 * caller), and the {@code chk_goal_layer_time_fields} CHECK constraint, created in {@code V6} as
 * {@code chk_goal_layer_horizon} and widened under the broader name in {@code V7}.
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
	static final String TIME_FIELDS_RULE =
			"only a GOAL has a horizon, and it always has one; only a TASK may have a due date";

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

	/**
	 * A task's optional term, and the mirror of {@code horizon}. A {@code LocalDate} rather than a
	 * timestamp because "do piątku" is a day the user picks off a calendar, not a moment in a
	 * timezone — and {@code <input type="date">} sends exactly that.
	 */
	@Column(name = "due_date")
	private LocalDate dueDate;

	@Enumerated(EnumType.STRING)
	@Column(name = "category_code", length = 32)
	private LifeDomain category;

	@Column(name = "completed_at")
	private OffsetDateTime completedAt;

	/**
	 * Quiet until this day (S-04b) — written by three of the four FR-013 answers, which differ only
	 * in the default they pick. A {@code LocalDate} like {@code dueDate}, and compared against the
	 * user's local date for the same reason: a snooze is "come back on Thursday".
	 */
	@Column(name = "remind_after")
	private LocalDate remindAfter;

	/** "Nigdy" (FR-013) — out of the running until {@link #restore}, never deleted. */
	@Column(name = "withdrawn_at")
	private OffsetDateTime withdrawnAt;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Goal() {
		// JPA requires a no-arg constructor.
	}

	public Goal(UUID userId, String content, GoalLayer layer, GoalHorizon horizon, LocalDate dueDate,
			LifeDomain category) {
		this.userId = Objects.requireNonNull(userId, "userId");
		update(content, layer, horizon, dueDate, category);
	}

	/**
	 * Full-replace edit, and the only write path — the constructor delegates here too, so the field
	 * list and the invariant check are each stated once. Behind {@code PUT /api/goals/{id}} it also
	 * covers every conversion between the three layers, which is why it re-checks: a dream that
	 * becomes a task sheds nothing and may gain a term; a goal that becomes one must drop its horizon
	 * in the same call, which is precisely what re-checking catches.
	 *
	 * <p>{@code final} because a constructor calls it — Hibernate needs the class and its accessors
	 * open, never a business method.
	 */
	public final void update(String content, GoalLayer layer, GoalHorizon horizon, LocalDate dueDate,
			LifeDomain category) {
		this.content = Objects.requireNonNull(content, "content");
		this.layer = Objects.requireNonNull(layer, "layer");
		if (!hasConsistentTimeFields(layer, horizon, dueDate)) {
			throw new IllegalArgumentException(TIME_FIELDS_RULE + ", got " + layer
					+ " with horizon " + horizon + " and due date " + dueDate);
		}
		this.horizon = horizon;
		this.dueDate = dueDate;
		this.category = category;
	}

	/**
	 * Mark done at the given moment. Truly idempotent: an entry that is already done keeps its
	 * original moment, because {@link #update} re-asserts completion on every full-replace PUT and
	 * the SPA sends the entry's own state back with each edit — re-stamping here would move the date
	 * every time someone fixes a typo, and the moment S-03 reads is unrecoverable once overwritten.
	 * Going back through {@link #reopen} and completing again is the only way to set a new one.
	 *
	 * <p>Truncated to microseconds because {@code timestamptz} holds no more: an untruncated clock
	 * (nanosecond-capable on Linux, not on macOS) would make the write response disagree with every
	 * later read of the same row, and the rounding is invisible until it isn't.
	 */
	public void complete(OffsetDateTime at) {
		Objects.requireNonNull(at, "at");
		if (this.completedAt == null) {
			this.completedAt = at.truncatedTo(ChronoUnit.MICROS);
		}
	}

	/** Back to active — the only way back, short of deleting the entry outright. */
	public void reopen() {
		this.completedAt = null;
	}

	/**
	 * Quiet this entry until the given day (S-04b, FR-013). Deliberately on the {@code goal} row and
	 * not on the proposal: the user asked for it, so it is a real interaction — which is what keeps
	 * {@code ProposalSelector} honest when it reads {@code updated_at} as "when the user last
	 * engaged". The date is inclusive: on the day itself the entry is eligible again.
	 */
	public void snoozeUntil(LocalDate day) {
		this.remindAfter = Objects.requireNonNull(day, "day");
	}

	/**
	 * "Nigdy": out of the running until the user restores it. Idempotent in the same way
	 * {@link #complete} is, and for the same trap — {@code PUT /api/goals/{id}} is full-replace, so
	 * the SPA re-asserts withdrawal on every edit of a withdrawn entry, and re-stamping would move
	 * the date each time someone fixes a typo.
	 */
	public void withdraw(OffsetDateTime at) {
		Objects.requireNonNull(at, "at");
		if (this.withdrawnAt == null) {
			this.withdrawnAt = at.truncatedTo(ChronoUnit.MICROS);
		}
	}

	/** Back into the running, and the reason "nigdy" is reversible rather than a delete. */
	public void restore() {
		this.withdrawnAt = null;
	}

	/**
	 * The layer × time-fields rule, stated once in Java so the aggregate and both request DTOs cannot
	 * drift apart — one conjunct per time field, in the order {@link #TIME_FIELDS_RULE} names them.
	 */
	static boolean hasConsistentTimeFields(GoalLayer layer, GoalHorizon horizon, LocalDate dueDate) {
		return (horizon != null) == (layer == GoalLayer.GOAL)
				&& (dueDate == null || layer == GoalLayer.TASK);
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

	public LocalDate getDueDate() {
		return dueDate;
	}

	public LifeDomain getCategory() {
		return category;
	}

	public OffsetDateTime getCompletedAt() {
		return completedAt;
	}

	public LocalDate getRemindAfter() {
		return remindAfter;
	}

	public OffsetDateTime getWithdrawnAt() {
		return withdrawnAt;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
