/**
 * Security and per-user isolation seam for 2do AI (S-01, account-and-auth).
 *
 * <p>This package holds the decided authentication wiring ({@link com.thedariusz.todoai.security.SecurityConfig}
 * — a server-side session cookie, in-memory, Spring Security 6; not JWT) and the two pieces that
 * make per-user data isolation a <em>named, single</em> seam rather than a convention scattered
 * across repositories:
 *
 * <ul>
 *   <li>{@link com.thedariusz.todoai.security.CurrentUser} — the one accessor that yields the
 *       authenticated user id, read from the security context with no DB round-trip. Every per-user
 *       query scopes through it; no code trusts a client-supplied id.</li>
 *   <li>{@link com.thedariusz.todoai.security.UserOwned} — the marker every per-user aggregate
 *       implements, so the isolation seam is scannable by a future structural guard (ArchUnit / RLS).</li>
 * </ul>
 *
 * <p><b>The convention, stated once:</b> a per-user aggregate implements {@code UserOwned}, carries a
 * {@code user_id} column, is read only through a {@code CurrentUser}-scoped finder, and has its rows
 * removed for FR-019 by a registered {@code PerUserDataDeleter} (S-01 Phase 2). Structural
 * enforcement of the convention (an ArchUnit rule, Postgres RLS) is intentionally out of scope in
 * S-01 — this package lays only the hook such enforcement attaches to.
 */
package com.thedariusz.todoai.security;
