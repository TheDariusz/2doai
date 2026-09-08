# Email Verification at Sign-up — Plan Brief

> Full plan: `context/changes/email-verification/plan.md`

## What & Why

Registration is open to the world with no proof that the person owns the address. That makes
the natural rhythm a delayed spam relay: register `victim@example.com`, write an advert into an
overdue task, and `2doai.app` mails it within a week. A 6-digit code sent to the address, with
nothing working until it is entered, closes that path while keeping sign-up open to anyone.

## Starting Point

`RegistrationService` creates the user and publishes `UserRegistered` in one transaction; the
scheduler adopts the account immediately and e-mails it. `UserPrincipal.isEnabled()` is
hardcoded `true`. Failures already carry Problem-JSON `type` URNs the SPA discriminates on, and
`User` is written only through targeted repository updates.

## Desired End State

A new account cannot log in and is never e-mailed a proposal until its owner enters the code.
The SPA walks sign-up → `/verify` → `/login`. An unverified login with the right password lands
on `/verify`; with a wrong password it fails exactly as today. Existing accounts notice nothing.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Gate mechanism | Code e-mailed to the address, not an invite list | Sign-up stays open; the abuse is unproven addresses, not strangers |
| Unverified account | Cannot log in at all | One hook (`isEnabled`), no half-authenticated state anywhere in the API |
| Code | 6 digits, 15 min, 5 attempts, BCrypt via the existing encoder | Easy to retype; the attempt cap closes online guessing; no new dependency |
| Login error order | Disabled check after the password check | A wrong password on an unverified account must stay a generic 401 |
| Dead unverified rows | Re-registration overwrites them | The owner always wins by entering the code; no scheduled cleanup (Neon idleness rule) |
| Throttle | In-memory per address: 60 s cooldown, 5/hour | Protects the 100/day Resend quota with zero schema; per-IP is the next issue |
| After the code | Redirect to `/login` with a notice | `POST /api/sessions` stays the only session-creation path |
| Code e-mail | Account language, plain text | Same rule as `ProposalTemplatePl/En`; no FR-009-style exception |
| Sending | After commit, failure → 503, row stays | No Hikari connection pinned across SMTP; overwrite makes the leftover harmless |

## Scope

**In scope:** V13 migration + backfill; `UserVerified` replacing `UserRegistered`; scheduler on
verified accounts only; `POST /api/verifications`, `POST /api/verification-codes`; login 403
`email-not-verified`; localized code e-mail; recording mail fake for tests; URN contract guard;
`/verify` screen; docs walk, runbook, README, diagrams.

**Out of scope:** per-IP rate limits and the Pages→Fly shared secret (next issue); magic links;
auto-login; address change; cleanup job; global daily mail cap; per-account LLM cap.

## Architecture / Approach

Aggregate gains four columns and `isEmailVerified()`; all writes are `@Modifying` updates.
`EmailVerificationService` issues (hash + send) and verifies (compare, mark, publish).
`VerificationThrottle` is a pruned in-memory map. `ProblemDetailsSecurityHandler` maps
`DisabledException` to the URN; `ApiExceptionHandler` maps verification failure (403), throttle
(429) and mail failure (503). Contract and Java move in one commit; the SPA reads the URNs from
one file the backend guard checks.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Domain and data | V13 + backfill, aggregate state, targeted updates, `UserVerified`, scheduler filter | Forgetting the backfill locks out every real user |
| 2. API | Code at registration, two endpoints, login gate, throttle, contract, test fake | Disabled check before the password leaks the URN |
| 3. Frontend | `/verify` screen, transitions, copy, tests | Reload on `/verify` losing the address (fallback input) |
| 4. Docs, gate, deploy | Docs walk, runbook, `/check`, production sign-up smoke | Partial docs walk reads like a complete one |

**Prerequisites:** Resend and OpenRouter secrets already on Fly (no new secret); a Linear issue
and its worktree.
**Estimated effort:** 2–3 evening sessions.

## Open Risks & Assumptions

- Verification proves an address, not a person: a bot with many mailboxes still creates many
  accounts. The per-IP limit is the next issue and must follow quickly.
- Sign-ups now spend Resend quota; a burst of real interest could hit 100/day. Watch the Resend
  dashboard after launch.
- `@MockitoBean` vs a `@Primary` fake for `EmailSender` — the plan switches the one affected test
  to the fake; if Spring resolves it differently, name the bean.

## Success Criteria (Summary)

- A fresh sign-up cannot log in or receive a proposal until the e-mailed code is entered.
- Existing users log in unchanged after deploy.
- `/check` green and the docs page describes the verification flow.
