---
id: category-contract-guards
title: "Guard the category contract — code↔display_order pairing, a rollback-safe boot check, and the spec anchor promoted out of a change folder"
linear_id:
status: planned
created: 2026-08-07
updated: 2026-08-07
prd_refs:
  - FR-007 (11 stałych domen życia)
---

# Category contract guards

DEV-31 fixed the *values* that had drifted between `openapi.yaml`, `LifeDomain`
and the Flyway seed. It added no guard, so the mechanism that let six of eleven
codes rot unnoticed is still in place. `lessons.md:36` records the gap verbatim:
**"Still unguarded — the `LifeDomain` codes."**

Three invariants are documented in prose and enforced by nothing:

1. **The code↔`display_order` pairing.** `CategorySyncCheck:35` and
   `CategorySeedTest.codesMatchLifeDomainEnum` both compare codes as an
   order-agnostic `Set`; `displayOrderIsOneToElevenAndUnique` sorts the orders
   and never checks which code holds which. A migration swapping two orders
   passes every check while the SPA nav silently reorders.
2. **`LifeDomain`'s javadoc invariant** — "Declaration order matches the table's
   `display_order` (1..11)" — has no test behind it.
3. **`openapi.yaml`'s `x-extensible-enum`** mirrors the seed by comment only
   (`# in display_order, mirroring V2__seed_categories.sql`). Same drift class as
   DEV-31, still unwatched. The `AppLayout.test.tsx` fixture copies the codes,
   labels *and* order too.

Plus one deployment-semantics defect surfaced while planning: `CategorySyncCheck`
throws on **any** mismatch, so a 12th domain added by an expand-only migration
makes the *previous* image unbootable against the new schema — contradicting the
"backward-compatible / safe under an image rollback" rule in `CLAUDE.md`.

- Plan: `plan.md`
- Brief: `plan-brief.md`
