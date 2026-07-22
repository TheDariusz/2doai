# Account & Auth (S-01) — Plan Brief

> Full plan: `context/changes/account-and-auth/plan.md`
> Decided session model: `context/foundation/auth-session-model.md`
> API contract (Zalando-aligned, API-first): `context/changes/account-and-auth/openapi.yaml`

## What & Why

The first user-facing slice and the first **per-user isolation boundary** — the
contract every later data slice (goals, dreams, tasks, memory) inherits. It lets a
person register (email+password), log in / out, and permanently delete their account
and all data (FR-019), with unauthenticated visits to gated routes redirected to
login. The AI memory the product is built around requires a stable identity; this
slice creates it.

## Starting Point

The backend has an `AiMemory` aggregate, a `category` reference table, and one smoke
controller — but **no Spring Security** and **no `user` table** (the
`ai_memory.user_id` FK was intentionally deferred to this slice). The frontend is
still the Vite demo scaffold: no router, no API client, no auth. The Vite `/api`
proxy and the same-origin Pattern B deployment already exist.

## Desired End State

A new user registers, logs in, and lands in an app shell (header + a navigation over
the 11 life domains with placeholder content). Gated routes redirect when logged
out. "Delete account" (confirm + password re-entry) wipes the user and their AI
memory, and the email becomes free again. All mutations carry a CSRF token;
`/check` is green.

## Key Decisions Made

| Decision                     | Choice                                            | Why (1 sentence)                                                                 | Source   |
| ---------------------------- | ------------------------------------------------- | -------------------------------------------------------------------------------- | -------- |
| Session model                | Server-side cookie, in-memory, Spring Security 6  | Same-origin Pattern B + Neon-autosuspend cost rule + FR-019 revocability         | Decision |
| Account deletion (FR-019)    | App-orchestrated `AccountDeletionService`         | Explicit + testable; paired with an orphan-guard so a forgotten table fails loud | Plan     |
| Per-user isolation           | Query-scoping by authenticated userId             | Simplest for a solo dev at MVP scale; enforced via a shared accessor + tests     | Plan     |
| Isolation future-proofing     | `UserOwned` marker + scoped-access convention now | Lays the hook so ArchUnit enforcement or RLS is cheap to adopt later, not a retrofit | Plan  |
| `ai_memory` provisioning     | Eager, in the registration transaction            | "One memory per user" invariant holds from t=0; deferred FK immediately safe     | Plan     |
| `user` shape                 | Minimal identity (email, password_hash)           | YAGNI — display name / flags are later, expand-only additions                    | Plan     |
| CSRF delivery to the SPA     | `XSRF-TOKEN` cookie → `X-XSRF-TOKEN` header echo  | Idiomatic Spring Security 6 SPA pattern; one place in the API client             | Plan     |
| Email verification / reset   | None (format-validate only)                       | No email infra until FR-018; correct scope for a 1–10-user personal app          | Plan     |
| Hardening                    | Baseline (BCrypt, min-8, generic errors, CSRF)    | Real risks covered without infra; no rate-limit needed at this scale             | Plan     |
| Session lifetime             | Session cookie + ~30-min idle timeout, no remember-me | Matches in-memory model; a deploy logs everyone out anyway                    | Plan     |
| Frontend shell scope         | Fuller shell (layout + 11-domain nav)             | Nav is data-driven off the already-frozen domain list, so it pre-decides nothing | Plan     |
| Frontend stack               | React Router + Context + fetch                    | De-facto standard, minimal deps, mainstream patterns that transfer well          | Plan     |
| Testing depth                | Focused on security-critical paths                | Covers isolation, gating, deletion, client plumbing — TDD-first                  | Plan     |

## Scope

**In scope:** Spring Security config; `app_user` table + expand-only FK; `User`
aggregate + `Email` VO; register/login/logout/me/delete endpoints; eager ai_memory
provisioning; `AccountDeletionService` + orphan-guard; read-only `/api/categories`;
React app shell (router, CSRF-aware API client, auth context, protected routes,
login/register screens, logout + delete); data-driven 11-domain nav.

**Out of scope:** JWT / Redis / Neon-backed sessions; email verification / password
reset / magic-link; rate limiting / lockout; remember-me; OAuth / roles / sharing;
Postgres RLS; per-domain feature screens; E2E/Playwright.

## Architecture / Approach

Backend-first, four phases. Spring Security runs the decided cookie-session model; a
`CurrentUser` accessor is the single seam per-user queries scope through, and a
`UserOwned` marker names that seam so a structural guard (ArchUnit/RLS) is cheap to
bolt on later. Deletion is
orchestrated across a `PerUserDataDeleter` registry (ai_memory registers now); a
plain FK on `ai_memory.user_id` (no `ON DELETE CASCADE`) means a forgotten future
deleter fails loudly instead of orphaning. The frontend talks to `/api`
same-origin, sends the session cookie, and echoes the CSRF token from the
`XSRF-TOKEN` cookie.

## Phases at a Glance

| Phase                              | What it delivers                                              | Key risk                                                        |
| ---------------------------------- | ------------------------------------------------------------ | -------------------------------------------------------------- |
| 1. Backend security foundation     | Security config, `User` aggregate, V4/V5 migrations, gating  | Security-on regresses `@WebMvcTest`; reserved-word table name  |
| 2. Backend auth + FR-019 + isolation | Auth endpoints, deletion service + guard, categories, tests | Orphan-guard correctness; CSRF-deferred-token cookie not set   |
| 3. Frontend plumbing + auth screens | Router, API client, auth context, login/register, delete    | Dev `Secure`-cookie over http; 401/CSRF handling in the client |
| 4. Frontend fuller shell           | Layout chrome + data-driven 11-domain nav                    | Keeping the nav placeholder-only (no S-02 rework)              |

**Prerequisites:** F-01 (persistence baseline) — done. No new infra; Vite proxy +
Pattern B already in place.
**Estimated effort:** ~4–6 focused sessions across the 4 phases (backend 1–2,
frontend 2–3), with a manual checkpoint after each.

## Open Risks & Assumptions

- Spring Security 6's **deferred CSRF token** must be primed on first load (via `/me`)
  or the `XSRF-TOKEN` cookie is never set — a classic SPA footgun (mitigation in the
  plan's Critical Details).
- The **dev/prod cookie `Secure` split** must be env-driven, or login silently fails
  to persist in local http dev.
- App-orchestrated deletion is only as safe as its guard — the orphan-guard test is
  load-bearing, not optional.
- Isolation in S-01 proves the *mechanism* (only `ai_memory` is per-user yet); each
  later slice must test its own query-scoping. The `UserOwned` marker + `user_id`
  convention are laid now (cheapest while N=1) so a structural guard — ArchUnit or
  RLS — is additive later rather than a cross-slice retrofit.

## Success Criteria (Summary)

- A person can register, log in / out, and be correctly gated out when logged out.
- Deleting an account removes the user and all their data, and frees the email.
- Every mutating request is CSRF-protected and same-origin; `/check` is green.
