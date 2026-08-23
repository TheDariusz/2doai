# Goals and Dreams (S-02) — Plan Brief

> Full plan: `context/changes/goals-and-dreams/plan.md`

## What & Why

CRUD for the two non-task layers — long-term goals (content + horizon: this year / few months) and "someday" dreams (content, no timeframe) — with an optional category from the 11 life domains. This is the substrate of the product's north star: the proactive loop (S-04/S-05) operates on goals and dreams, so they must exist first. Linear DEV-19; PRD FR-004, FR-005, FR-007.

## Starting Point

S-01 delivered auth, per-user isolation, and the app shell with the 11-domain nav; F-01 seeded the `category` table; F-02 set the aggregate conventions (UUID v7, audit columns, `UserOwned`). Highest migration is V5; the `/cele` layers have no tables, no API, no UI.

## Desired End State

A logged-in user at `/cele` creates, edits, completes, and un-completes goals and dreams; a dream can be promoted to a goal in one edit; entries carry an optional life-domain category; completed items sit under a collapsed section. Data is per-user, survives to FR-019 deletion, and the new wire enums can't silently drift between spec, backend, and frontend.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Aggregate shape | **One aggregate**, `layer` discriminator + nullable `horizon` | S-04/S-05/S-08/S-09 all consume the union; only the horizon field differs |
| Naming | `goal` table, `goal/` package, `/api/goals` | Boring and greppable; a dream is a goal without a timeframe |
| Delete | **No delete endpoint** | FR-004/FR-005 deliberately omit it; S-04 "nigdy" + FR-019 cover removal stories |
| Completion | Reversible — nullable `completed_at` | One column is both flag and the timestamp S-03 will read; mistake-friendly |
| Layer conversion | Editable — dream ↔ goal via PUT | "Dream matures into a goal" is the core product story; one-aggregate makes it a field update |
| UI shape | Single `/cele` page | One screen delivers the outcome; S-08 owns the unified filtered view |
| Completed display | Collapsed `<details>` per layer | Active list stays clean; un-complete stays reachable; zero JS |
| API filters | None — GET returns everything | Single-user scale; S-08 defines the real filter contract additively |
| Hardening | ArchUnit `UserOwned` rule + DB CHECK (layer×horizon) + contract-anchor test | The S-02 promotions the codebase already scheduled, plus the lessons.md rule applied at introduction time |
| Category mapping | `@Enumerated(STRING) LifeDomain` + DB FK to `category.code` | Enum names == codes (boot-guarded); unknown code fails as 400, FK is the backstop |
| Spec location | Extend `account-and-auth/openapi.yaml` in place | One authoritative anchor; promotion belongs to `category-contract-guards` |

## Scope

**In scope:** V6 migration (CHECK + FKs + indexes) · `Goal` aggregate/repo/service/controller (`GET`/`POST /api/goals`, `PUT /api/goals/{id}`) · first domain 404 (no existence leak) · `GoalDataDeleter` (FR-019) · ArchUnit promotion · spec extension + contract-anchor test · `/cele` page with create/edit/complete + category picker · docs (data-model.md, roadmap)

**Out of scope:** delete endpoint · server-side filters · AI auto-tag (S-09) · memory enrichment (S-03) · proposals (S-04) · per-domain lists / unified view (S-08) · moving openapi.yaml (`category-contract-guards`)

## Architecture / Approach

Follow the established file-for-file patterns: `AiMemory` for the entity, `auth/` for DTOs/handlers, `ApiTestBase` (REST Assured + Testcontainers) for tests, `AuthPage` idioms for the screen. The layer×horizon invariant is enforced at three depths — request DTO (`@AssertTrue` → 422), domain mutator, DB CHECK. Category picker reuses the domains already flowing through `<Outlet context>` — zero extra requests.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Persistence + guards | V6, `Goal` entity/repo, ArchUnit rule, persistence tests | First `@Enumerated` precedent; FK sweep in `AccountDeletionIntegrationTest` must accept `goal.user_id` |
| 2. API + contract | 3 endpoints, 404, FR-019 deleter, spec + anchor test, REST Assured suite | 422 vs 400 split (bean validation vs Jackson enum) must be asserted correctly |
| 3. Frontend | `/cele` page, nav link, tests, docs | Anchor test's frontend assertion lands only when Phase 3's file exists (same PR) |

**Prerequisites:** S-01 + F-01 merged (done); branch `thedariusz/dev-19-…` checked out; DEV-19 In Progress.
**Estimated effort:** ~2-3 sessions across 3 phases.

## Open Risks & Assumptions

- Content cap of 500 chars is a judgment call (PRD says only "treść") — mirrored client-side, cheap to raise expand-only later.
- The contract-anchor test greps `GoalsPage.tsx` for enum literals; renaming that file requires updating the test (same coupling `AuthApiTest` already accepts).
- "Un-complete after S-03 memory enrichment" semantics deferred to S-03 — `completed_at` clearing is safe today because nothing consumes completion events yet.

## Success Criteria (Summary)

- `/check` fully green, including the new persistence, API, ArchUnit, and contract-anchor tests.
- Live flow works end-to-end: create → edit → dream→goal conversion → complete → un-complete → survives refresh; `/cele` is auth-gated.
- The layer/horizon literals cannot drift between openapi.yaml, backend enums, and frontend types without a red test.
