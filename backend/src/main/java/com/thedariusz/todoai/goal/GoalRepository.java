package com.thedariusz.todoai.goal;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/**
 * Spring Data access to the {@code goal} aggregate. Every finder is scoped by {@code userId} per the
 * {@link com.thedariusz.todoai.security.UserOwned} contract — including the by-id lookup, so a
 * client-supplied id can never reach a row it does not own (a foreign id and a nonexistent one are
 * deliberately indistinguishable: both yield the same 404).
 *
 * <p>That sentence is only true because this extends the bare {@link Repository} marker rather than
 * {@code JpaRepository}: the convenient inherited finders ({@code findById}, {@code findAll},
 * {@code getReferenceById}, {@code deleteById}) are <b>unscoped</b>, and the next person to need a goal
 * by id would otherwise reach for the one that compiles and skip the ownership check. The methods
 * below are the entire surface, so the isolation contract holds by construction instead of by
 * convention — with one named, documented exception, {@link #findPendingReminders}.
 *
 * <p>Unparameterized list + client-side grouping is deliberate at single-user scale — S-08 filters
 * by layer and category in the browser rather than here. A parameterized finder waits for a list
 * that outgrows one round-trip.
 */
public interface GoalRepository extends Repository<Goal, UUID> {

	Goal saveAndFlush(Goal goal);

	List<Goal> findByUserIdOrderByCreatedAtDesc(UUID userId);

	Optional<Goal> findByIdAndUserId(UUID id, UUID userId);

	/**
	 * The by-id read for a read-modify-write of the entry ({@code SELECT ... FOR UPDATE}). Every
	 * writer of an existing row goes through it — the PUT, a proposal answer, the rhythm's supersede —
	 * because each writes the whole row back: without the lock the last one to flush silently undoes
	 * the others (a snooze cleared by an edit, a completion reopened by a snooze).
	 *
	 * <p><b>Lock order: the goal row first, then its proposal.</b> Every path that holds both takes
	 * them in that order — a {@code DELETE} of the goal cascades into {@code proposal} the same way —
	 * so two of them can queue but never deadlock.
	 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<Goal> findWithLockByIdAndUserId(UUID id, UUID userId);

	/** Scoped like the finders, so an id the caller does not own deletes nothing and returns 0. */
	long deleteByIdAndUserId(UUID id, UUID userId);

	/** FR-019 erasure, driven by {@code GoalDataDeleter}. */
	void deleteByUserId(UUID userId);

	/**
	 * Every due-date reminder still owed, across all accounts (DEV-63) — <b>the one unscoped read on
	 * this repository</b>, and deliberately so: it is the reminder scheduler's boot load, a background
	 * job with no request and no current user, run once per JVM in the way
	 * {@code ProposalScheduler.loadSchedule} reads every account. It returns a projection, never an
	 * entity, so nothing it hands out can be written back to a row its caller does not own.
	 *
	 * <p>Only active, unsent tasks of accounts whose address is proved: the reminder writes to an
	 * address unprompted, which the rhythm only ever does for a verified account (DEV-51).
	 *
	 * @param fromDay the earliest due day still worth reading — yesterday in the user's zone, because a
	 *        late-evening term's grace can cross midnight; the caller drops what has already expired
	 */
	@Query("""
			select new com.thedariusz.todoai.goal.PendingReminderRow(g.id, g.userId, g.dueDate, g.dueTime,
				g.reminderLead)
			from Goal g join User u on u.id = g.userId
			where g.layer = com.thedariusz.todoai.goal.GoalLayer.TASK
				and g.dueDate >= :fromDay
				and g.reminderSentAt is null
				and g.completedAt is null
				and g.withdrawnAt is null
				and u.emailVerifiedAt is not null
			""")
	List<PendingReminderRow> findPendingReminders(LocalDate fromDay);

	/**
	 * Stamp the current term's reminder as sent (DEV-63), scoped like every other write here.
	 *
	 * <p><b>A bulk update, so the stamp does not move {@code updated_at}.</b> {@code ProposalSelector}
	 * reads that column as the last time the user engaged with the entry; machine bookkeeping saved
	 * through the entity would fire {@code @UpdateTimestamp} and reset the rhythm's neglect clock for
	 * a task the user never touched. A bulk update bypasses the annotation. The caller holds the row
	 * locked by {@link #findWithLockByIdAndUserId} and only reads that entity, so no flush rewrites it.
	 *
	 * @return 1 when this call stamped it, 0 when the row is gone, foreign, or already stamped
	 */
	@Modifying(flushAutomatically = true)
	@Query("""
			update Goal g set g.reminderSentAt = :now
			where g.id = :id and g.userId = :userId and g.reminderSentAt is null
			""")
	int markReminderSent(UUID id, UUID userId, OffsetDateTime now);
}
