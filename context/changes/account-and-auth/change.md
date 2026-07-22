---
id: account-and-auth
title: "Account & auth — email+password, server-side session cookie, per-user isolation, FR-019 delete, React app shell"
roadmap_id: S-01
status: implementing
created: 2026-07-22
updated: 2026-07-22
prd_refs:
  - FR-001 (założyć konto — email+hasło)
  - FR-002 (zalogować / wylogować)
  - FR-019 (usunięcie konta + wszystkich danych; potwierdzane, nieodwracalne)
  - PRD Access Control (multi-user, model płaski, brak trybu anonimowego, bramkowanie tras)
---

# Account & auth (S-01)

First user-facing slice and the first per-user isolation boundary — the contract
every later data slice (S-02 goals/dreams, S-03 memory seed, S-07 current tasks)
inherits. Establishes **Spring Security 6** on the decided session model
(`context/foundation/auth-session-model.md`): a server-side session cookie
(`HttpOnly; Secure; SameSite=Strict`), in-memory on the single Fly machine,
email+password, CSRF via SameSite + Spring's built-in token — **not JWT**.

Delivers: register / login / logout, gated routes that redirect unauthenticated
users to login, and **FR-019** full account + data deletion (confirmed,
irreversible). Turns the `ai_memory.user_id` deferred FK into a real constraint
(expand-only `ALTER`). Also builds the **React app shell** from today's demo
scaffold: router, CSRF-aware API client, auth context, auth screens, and a
data-driven 11-domain navigation shell.

Key decision pre-settled in `context/foundation/auth-session-model.md` (cookie vs
JWT — cookie, with rationale grounded in same-origin Pattern B + Neon autosuspend).

Unblocks: S-02 (goals-and-dreams) and all subsequent per-user data slices.

- Plan: `plan.md`
- Brief: `plan-brief.md`
