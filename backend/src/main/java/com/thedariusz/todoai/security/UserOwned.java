package com.thedariusz.todoai.security;

import java.util.UUID;

/**
 * Marker for a <em>per-user aggregate</em>: it carries a {@code user_id} column and exposes its
 * owner through {@link #getUserId()}. Establishing this seam now — while {@code AiMemory} is the
 * only per-user entity (N = 1) — is what makes a stronger structural guard cheap to adopt later
 * rather than a cross-slice retrofit.
 *
 * <p><b>The isolation contract</b> every per-user aggregate inherits:
 * <ol>
 *   <li>it implements {@code UserOwned} and carries a {@code user_id} column;</li>
 *   <li>it is read only through a finder scoped by the authenticated user id from
 *       {@link CurrentUser#requireId()} (e.g. {@code findByUserId}) — never by a client-supplied
 *       id;</li>
 *   <li>its rows are deleted for FR-019 by a registered {@code PerUserDataDeleter} (S-01 Phase 2).</li>
 * </ol>
 *
 * <p>Because the seam is now <em>named and scannable</em>, a future enforcement layer keys off it
 * additively: an ArchUnit rule — "every {@code @Entity} mapping a {@code user_id} column implements
 * {@code UserOwned}" (arrives with S-02, the second per-user entity) — and, further out, Postgres
 * RLS policies on the universal {@code user_id} column. Neither enforcement is built in S-01; this
 * marker is only the hook they attach to (see the plan's <em>What We're NOT Doing</em>).
 */
public interface UserOwned {

	UUID getUserId();
}
