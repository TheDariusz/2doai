# Category Contract Guards — Plan Brief

> Full plan: `context/changes/category-contract-guards/plan.md`
> Accepted prior: `context/foundation/lessons.md:29-36` ("A contract value duplicated across the stack needs one guard that spans the boundary")

## What & Why

The 11 life-domain codes are copied across seven places and checked in only one direction. DEV-31 fixed the values after six of eleven codes had silently rotted in `openapi.yaml`, but added no guard — so the mechanism that allowed the drift is untouched and will allow it again. `lessons.md:36` records the gap in its own words: **"Still unguarded — the `LifeDomain` codes."** This plan is that entry's remedy.

## Starting Point

Four overlapping guards exist (`CategorySyncCheck`, four `CategorySeedTest` tests, DB constraints, Flyway checksums) and all of them compare codes as an order-agnostic `Set`. `LifeDomain`'s javadoc claims declaration order matches `display_order`; nothing tests it. `openapi.yaml`'s `x-extensible-enum` claims to mirror the seed *in a comment*; nothing compares them. `AppLayout.tsx:13` already documents the hole out loud: *"ordering live in the Flyway seed alone and are guarded by nothing."*

## Desired End State

Editing `LifeDomain`, the Flyway seed, `openapi.yaml`, or the SPA fixture without carrying the change to the others turns a test red. Adding a 12th domain via an expand-only migration boots cleanly on both the new image and the previous one. The contract anchor sits at a path that survives `/10x-archive`.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Anchor of truth | `openapi.yaml` | One artifact both sides are compared against, rather than each side asserting its own copy. | Lessons |
| Boot guard direction | Tolerant (`enum ⊆ db`) at boot, strict in tests | An expand-only 12th domain must not make the previous image unbootable, but CI still catches seed drift because tests run against a deterministic seed. | Plan |
| Pairing enforcement | Assert enum declaration order | ~5 lines closes both the pairing gap and the unenforced javadoc at once, without moving Polish labels into Java. | Plan |
| Anchor location | Promote to `context/foundation/openapi.yaml` | The living contract should not sit in a completed change's folder that `/10x-archive` relocates — and `AuthApiTest:287` already hardcodes that fragile path. | Plan |
| Guard coverage | Anchor + SPA fixture only | These are the two executable copies that can pass while wrong; diagrams and the docs page stay a documented manual step. | Plan |
| Guard home | Backend test | Reuses the accepted `AuthApiTest` precedent and gets type-safe `LifeDomain.values()`; Vitest cannot read above the Vite root without widening `server.fs.allow`. | Lessons |

## Scope

**In scope:** the code↔`display_order` pairing assertion; a rollback-safe `CategorySyncCheck`; a cross-boundary `CategoryContractTest` covering the anchor and the SPA fixture (codes, order, and labels); moving `openapi.yaml`; revising the `lessons.md` bullet.

**Out of scope:** `docs/index.html` and the `.drawio`/`.svg` diagrams (stay manual); moving `name_pl`/`display_order` into the enum; rewriting completed change records; actually adding a 12th domain; any frontend production code.

## Architecture / Approach

`LifeDomain` stays the code-side source of truth and the seed keeps owning labels and order. One new backend test reads three sources — the enum, the parsed anchor, and the SPA fixture text — plus the seeded rows from Testcontainers, and holds them against each other. The boot check becomes deliberately one-directional: a DB row the enum doesn't know is a rolled-back image and is tolerated; an enum constant with no row is seed drift and still fails.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Promote the anchor | `openapi.yaml` at `context/foundation/`, references repointed | Pure churn with nothing to demo; a missed reference breaks `AuthApiTest` |
| 2. Pairing + rollback semantics | Pairing test, one-directional boot guard, javadoc warning | Relaxing the guard is easy to over-relax and lose seed-drift detection |
| 3. Cross-boundary guard | `CategoryContractTest`, revised `lessons.md` | Backend suite now reds on frontend edits — surprising until the test name is read |

**Prerequisites:** none — no schema change, no new dependency (SnakeYAML 2.6 is already on the classpath transitively).
**Estimated effort:** ~1 session; Phase 1 is minutes, Phases 2-3 are a few dozen lines of test code each.

## Open Risks & Assumptions

- Making enum declaration order load-bearing means reordering constants becomes a breaking change — mitigated by javadoc, but it is a real new constraint (*Effective Java* Item 35 territory).
- The tolerant boot guard accepts a stray extra row in production, which `CategoryController` would serve straight into the nav. Judged the correct trade against an unbootable rolled-back image; the strict test catches it in CI first.
- Cross-project paths from a Maven test are fragile by nature. Phase 1 reduces the exposure but does not remove it; a frontend file rename still breaks the backend suite.

## Success Criteria (Summary)

- Reordering two `LifeDomain` constants, changing a code in the anchor, or changing a label in the SPA fixture each turns a test red — and the message names the file to fix.
- A 12th domain can be added by an expand-only migration and rolled back without an unbootable image.
- `lessons.md`'s "still unguarded" list shrinks to only what is genuinely still manual.
