package com.thedariusz.todoai.proposal;

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

import com.thedariusz.todoai.security.UserOwned;
import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.annotations.UpdateTimestamp;
import org.hibernate.annotations.UuidGenerator;
import org.hibernate.type.SqlTypes;

/**
 * A proposal the user was actually shown, and what they did about it (S-04b, FR-013/FR-018).
 *
 * <p><b>Why this row exists at all</b>, when the engine could re-select on every press: because a
 * second press must return the <em>same</em> proposal rather than pay for a second model call, and
 * because "at most one pending proposal" (FR-018) needs somewhere to be true. The uniqueness itself
 * is not enforced here — it is the {@code idx_proposal_one_pending} partial index, since a
 * service-level check would race with itself on a double-click.
 *
 * <p><b>What is on this row and what is not.</b> Everything the machine writes on its own — the
 * phrased message, the frozen neglect count, the generated first step — lives here. What the
 * <em>user</em> performed (the snooze, the withdrawal) is written to the {@code goal} row instead,
 * because {@code ProposalSelector} reads {@code goal.updated_at} as "when the user last engaged"
 * and bookkeeping stamped there would silently reset the neglect clock. That split is the whole
 * reason this aggregate exists rather than a handful of extra columns on {@code goal}.
 *
 * <p>{@link #answer} is a method rather than two setters so the answer and the moment it arrived can
 * never disagree, and so a second answer is a loud {@link ProposalAlreadyAnsweredException} — the
 * same 409 the service's pre-check raises — instead of a silent overwrite.
 */
@Entity
@Table(name = "proposal")
public class Proposal implements UserOwned {

	/** Which arm phrased the message — a demo has to be able to tell Sonnet from the fallback. */
	public enum Source {

		/** Phrased by the model. */
		LLM,

		/** Phrased by {@code ProposalTemplate} because the model call failed. */
		TEMPLATE
	}

	@Id
	@UuidGenerator(style = UuidGenerator.Style.VERSION_7)
	@Column(nullable = false, updatable = false)
	private UUID id;

	@NotNull
	@Column(name = "user_id", nullable = false, updatable = false)
	private UUID userId;

	/**
	 * The entry this proposal is about, held as a plain id rather than a {@code @ManyToOne}: the
	 * proposal is its own aggregate, and a reference would invite loading (and lazily mutating) a
	 * {@code Goal} through it — exactly the bookkeeping write the split above exists to prevent.
	 */
	@NotNull
	@Column(name = "goal_id", nullable = false, updatable = false)
	private UUID goalId;

	@NotBlank
	@Column(nullable = false)
	private String message;

	/** Frozen at phrasing time: the message quotes this number, so recomputing it would contradict it. */
	@Column(name = "neglected_days", nullable = false)
	private int neglectedDays;

	@NotNull
	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 16)
	private Source source;

	@Enumerated(EnumType.STRING)
	@Column(length = 16)
	private ProposalAnswer answer;

	@Column(name = "answered_at")
	private OffsetDateTime answeredAt;

	/**
	 * FR-014's bullets as raw JSON, mapped the way {@code ai_memory_episode.payload} is.
	 *
	 * <p><b>The bare array</b> {@code ["...", "..."]} — the steps themselves, not the
	 * {@code {"steps": [...]}} envelope {@link FirstStep}'s schema makes the <em>model</em> answer
	 * in — {@code ProposalService} unwraps it before the JSON gets here. V8's column comment says
	 * otherwise and is wrong; correcting an applied migration costs a Flyway checksum, so this is
	 * the statement to trust.
	 */
	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "first_step")
	private String firstStep;

	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private OffsetDateTime createdAt;

	@UpdateTimestamp
	@Column(name = "updated_at", nullable = false)
	private OffsetDateTime updatedAt;

	protected Proposal() {
	}

	public Proposal(UUID userId, UUID goalId, String message, long neglectedDays, Source source) {
		this.userId = Objects.requireNonNull(userId, "userId");
		this.goalId = Objects.requireNonNull(goalId, "goalId");
		this.message = Objects.requireNonNull(message, "message");
		// A day count that needed more than an int would mean an entry idle for 5.8 million years.
		this.neglectedDays = Math.toIntExact(neglectedDays);
		this.source = Objects.requireNonNull(source, "source");
	}

	/**
	 * Record the user's answer, once. Truncated to microseconds for the reason {@code Goal#complete}
	 * gives: {@code timestamptz} holds no more, and an untruncated clock would let the response carry
	 * a moment no later read of the row can return.
	 *
	 * <p>The refusal is the same exception {@code ProposalService}'s pre-check throws, so both arrive
	 * at the 409 {@code ApiExceptionHandler} publishes. The service check exists only to refuse before
	 * the model call is paid for; this one is what covers two answers in flight at once, and it must
	 * not be the arm that turns into a 500.
	 *
	 * @throws ProposalAlreadyAnsweredException if this proposal has already been answered
	 */
	public void answer(ProposalAnswer answer, OffsetDateTime at) {
		if (!isPending()) {
			throw new ProposalAlreadyAnsweredException(id);
		}
		this.answer = Objects.requireNonNull(answer, "answer");
		this.answeredAt = Objects.requireNonNull(at, "at").truncatedTo(ChronoUnit.MICROS);
	}

	/** Store FR-014's generated bullets, so a reload shows the plan the user already read. */
	public void recordFirstStep(String stepsJson) {
		this.firstStep = stepsJson;
	}

	/** Read off {@code answeredAt} — the same column {@code idx_proposal_one_pending} is partial on. */
	public boolean isPending() {
		return answeredAt == null;
	}

	public UUID getId() {
		return id;
	}

	@Override
	public UUID getUserId() {
		return userId;
	}

	public UUID getGoalId() {
		return goalId;
	}

	public String getMessage() {
		return message;
	}

	public long getNeglectedDays() {
		return neglectedDays;
	}

	public Source getSource() {
		return source;
	}

	public ProposalAnswer getAnswer() {
		return answer;
	}

	public OffsetDateTime getAnsweredAt() {
		return answeredAt;
	}

	public String getFirstStep() {
		return firstStep;
	}

	public OffsetDateTime getCreatedAt() {
		return createdAt;
	}

	public OffsetDateTime getUpdatedAt() {
		return updatedAt;
	}
}
