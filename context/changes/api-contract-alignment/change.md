---
id: api-contract-alignment
title: "Align openapi.yaml with the implementation — a `type` URN for the re-auth 403, the real 11 domain codes, the undocumented 503"
linear_id: DEV-31
status: implemented
created: 2026-08-05
updated: 2026-08-05
prd_refs:
  - FR-007 (11 stałych domen życia)
  - FR-019 (usunięcie konta — potwierdzane hasłem, nieodwracalne)
---

# API contract alignment (DEV-31)

`context/changes/account-and-auth/openapi.yaml` drifted from the code it
describes. The frontend (S-01 fazy 3-4, DEV-21) was the contract's first
consumer and had to work around the drift, which is how it surfaced.

Three corrections, one design decision:

1. **`DELETE /api/users/me` 401 vs 403** — the spec says a wrong password is
   401; the implementation returns 403 and `AuthApiTest` asserts it. The spec is
   the stale one. Decision taken: **keep 403, add a `type` URN**
   (`urn:2doai:problem:re-auth-failed`) so a client discriminates the two 403s
   machine-readably instead of by prose in `detail`.
2. **The 11 life-domain codes** — the spec's `x-extensible-enum` shares only 5
   of 11 codes with `LifeDomain` and the Flyway seed.
3. **The undocumented 503** — `ProblemDetailsSecurityHandler` deliberately
   answers 503 (not 401) when authentication fails on infrastructure, and no
   operation in the spec says so.

Decision record: new section in `context/foundation/auth-session-model.md`.

- Plan: `plan.md`
- Brief: `plan-brief.md`
