# API Contract Alignment (DEV-31) — Plan Brief

> Full plan: `context/changes/api-contract-alignment/plan.md`

## What & Why

`openapi.yaml` for the account-and-auth slice disagrees with the code it describes. The frontend was
the contract's first consumer and had to work around the drift, which is how it surfaced. Two
disagreements were reported in DEV-31; a third was found while planning. The 401-vs-403 one is not a
typo — it exposes that two different 403s on `DELETE /api/users/me` are indistinguishable to a client,
which is why `AccountMenu` shows hedged copy instead of *"Nieprawidłowe hasło."*

## Starting Point

`UserController` throws on a wrong confirmation password, `ApiExceptionHandler:52` maps it to 403, and
`AuthApiTest` asserts 403 — while `openapi.yaml:122-125` documents the inverse. Both that 403 and the
CSRF 403 from `ProblemDetailsSecurityHandler:72` emit identical `status`, `title` and `type`; only the
`detail` prose differs, which RFC 9457 says clients must not parse. Separately, the spec's eleven
life-domain codes share only five with `LifeDomain` and the Flyway seed, and the 503 that
`ProblemDetailsSecurityHandler:53` deliberately returns on a Neon cold start is documented nowhere.

## Desired End State

The spec describes what the server does on all three points. A client tells a mistyped password from
any other 403 by reading `type`, and `AccountMenu` says *"Nieprawidłowe hasło."* again. Nothing about
the deletion flow changes for a user who types the right password.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) |
| --- | --- | --- |
| Status for a failed re-auth | Keep **403**, add a `type` URN | Smallest correct diff — `Problem.type` is already in the schema, no status churn, and the existing Javadoc's argument for 403 stands. |
| Discriminator form | `urn:2doai:problem:re-auth-failed` | Stable forever, needs no hosting, and cannot rot into a 404 the way an `https://` URL to a non-existent docs site would. |
| Spec scope | Two reported drifts **+ the missing 503** | Same bug class, found in the same read, and it is a response the SPA can actually receive today. |
| Where the decision lives | New section in `auth-session-model.md` | The durable home for auth semantics; it survives the change folder being archived and records the *rejected* 401/422 options, which the code comment does not. |
| Type the CSRF 403 too? | No | "Is it the re-auth URN or not" answers the only question a client has; a second URN doubles the contract surface for no new capability. |
| Add a spec↔enum drift guard? | Deferred | Fix the drift now, not the class of drift — separate ticket. |

## Scope

**In scope:** the `type`/`title` on the re-auth 403; `AuthApiTest` assertions for both 403 shapes;
`openapi.yaml` corrections (401/403, the eleven codes, a 503 on `POST /sessions`); the decision record;
`ApiError` carrying `type`; `AccountMenu`'s precise copy; frontend tests.

**Out of scope:** relocating `openapi.yaml` out of the change folder that `/10x-archive` will one day
move; an automated spec↔enum drift guard; extending `CategorySyncCheck` to `name_pl` / `display_order`;
a full operation-by-operation spec audit; any status change.

## Architecture / Approach

One line in `ApiExceptionHandler` stamps a URN on the re-auth `ProblemDetail`; `client.ts` stops
discarding `type` when it parses the Problem JSON; `AccountMenu` branches on the URN instead of the
status. The spec is edited to match, and the reasoning is written down where the next person will look
for it. Backend ships first so the frontend keys off a URN that exists on the wire.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Backend + contract | `type` URN + title on the re-auth 403, both 403s pinned by `AuthApiTest`, all three spec corrections, decision record | Very low — additive to a response body, no status or schema change |
| 2. Frontend | `ApiError.type`, precise deletion copy, tests for both 403 shapes | Very low — the existing `AccountMenu` test passes without the fix, so writing it first matters |

**Prerequisites:** none — `master` is clean and nothing blocks this.
**Estimated effort:** one session; roughly 30 lines of production code across four files, plus the spec
and doc edits.

## Open Risks & Assumptions

- **DEV-31's motivating scenario does not hold.** The ticket argues that an expired session leaves a
  stale CSRF token and strands the user on a 403. That needs a session-bound token repository; this
  project uses `CookieCsrfTokenRepository.withHttpOnlyFalse()`, so the delete passes CSRF, gets a 401,
  and the SPA drops to `/login` as intended. The contract ambiguity being fixed here is real; the
  user-facing trap was not. If you disagree with that reading, the priority of this ticket changes
  before the plan does.
- The URN is a wire value pinned in three places by design (handler, `AuthApiTest`, spec) — a rename
  must touch all three, and the tests are what enforce it.
- The spec stays in a change folder that will eventually be archived. Fixed contents, unfixed home.

## Success Criteria (Summary)

- A wrong confirmation password shows *"Nieprawidłowe hasło."* and leaves the session intact
- The spec's 403, its eleven domain codes, and the login 503 all match what the server does
- A future refactor that types both 403s identically again fails `AuthApiTest`
