# Goals and Dreams (S-02) Implementation Plan

## Overview

Full-stack CRUD-minus-delete for the two non-task layers: long-term goals (content + horizon: this year / few months) and "someday" dreams (content, no timeframe), with an optional category from the 11 life domains. One `goal` aggregate with a `layer` discriminator (GOAL|DREAM) and a nullable `horizon` — decided 2026-08-17, recorded in `change.md`. Linear: DEV-19. PRD: FR-004, FR-005, FR-007.

## Current State Analysis

- Backend has auth + per-user isolation (S-01), the AI-memory aggregate (F-02), and the category reference table (F-01). Highest Flyway migration is **V5** → this slice adds **V6**.
- `AiMemory` (`ai/memory/AiMemory.java:41-79`) is the entity template: UUID v7 via `@UuidGenerator(style = Style.VERSION_7)`, `OffsetDateTime` + `@CreationTimestamp`/`@UpdateTimestamp`, `implements UserOwned`, getters-only with intent-revealing mutators, invariants in the constructor mirrored by Jakarta annotations on fields.
- Isolation contract: reads go through a `findByUserId`-style finder scoped by `CurrentUser.requireId()` (`security/CurrentUser.java:21-37`), never a client-supplied id. `UserOwnedConventionTest` guards the convention by hand (N=1) — its javadoc schedules the ArchUnit promotion **for S-02**. ArchUnit is not yet in `pom.xml`.
- FR-019 seam: `account/PerUserDataDeleter` discovered via `List<PerUserDataDeleter>` injection; `AccountDeletionIntegrationTest:107-138` queries `information_schema` and fails on any `user_id` column lacking a `NO ACTION` FK to `app_user`.
- Errors are RFC 9457 Problem JSON. Bean-validation failures → **422** with a generic detail (`auth/ApiExceptionHandler.java:38-48`); malformed/unreadable bodies (including bad enum literals) → 400 via `ResponseEntityExceptionHandler`. There is **no "resource not found for this user" exception yet** — S-02 introduces the first.
- No `@Enumerated` mapping exists anywhere yet — S-02 sets the precedent.
- `/api/goals` will be authenticated by default (`SecurityConfig.java:86-114` — `anyRequest().authenticated()`); no security config change.
- The authoritative OpenAPI spec lives at `context/changes/account-and-auth/openapi.yaml` (header declares it authoritative). The pending `category-contract-guards` change owns promoting it to a neutral path — S-02 extends it **in place**.
- Frontend: react-router 8 with `ProtectedRoute` → `AppLayout`; the 11 domains are fetched once in `AppLayout.tsx:19-26` and passed to nested routes via `<Outlet context={domains}>` (`AppLayout.tsx:47`) — a category picker costs zero extra requests. Forms are uncontrolled + `FormData` + native validation (`AuthPage.tsx`), errors render as `<p role="alert">`, copy is Polish, styling is one global `index.css`. Tests: Vitest + RTL, `vi.stubGlobal('fetch', ...)`, helpers in `src/test/auth.tsx`.

## Desired End State

A logged-in user can, at `/cele`: create a goal (content + horizon + optional category) or a dream (content + optional category), edit any field including flipping layer (dream ↔ goal), complete and un-complete entries, and see active entries grouped by layer with completed ones under a collapsed section. Everything is per-user isolated, survives account deletion (FR-019), and the new wire enums are guarded against drift.

Verify: `/check` green; manual flow in the browser against the live backend (create → edit → convert → complete → un-complete → refresh → still there).

### Key Discoveries:

- `ApiTestBase` gives `givenLoggedInUser()`, `csrfAware()`, `newBrowser()` for isolation tests — no MockMvc anywhere (memory: REST Assured only).
- `CategoryRepository` javadoc already names S-02 as a consumer; `LifeDomain` enum names == `category.code` values, guarded at boot by `CategorySyncCheck` — so the entity can map category as `@Enumerated(STRING) LifeDomain` with a DB FK as backstop.
- `AuthApiTest:274-297` is the contract-anchor precedent: backend test reads `openapi.yaml` + a frontend source file and asserts the wire literal appears in both (lives backend-side because Vitest can't read above the Vite root).
- Migrations carry a header comment (what/why + "expand-only, safe under an image rollback") and explicit `CREATE INDEX` on FK columns (Postgres doesn't auto-index them, `V3:38-41`).
- Collections are wrapped objects (`{items: [...]}`), never bare arrays; JSON is snake_case (`spring.jackson.property-naming-strategy=SNAKE_CASE`).

## What We're NOT Doing

- **No DELETE endpoint** — FR-004/FR-005 deliberately omit delete (unlike FR-003); S-04's "nigdy" withdrawal and FR-019 cover the removal stories.
- **No server-side filters** (`?layer=`, `?category=`) — GET returns everything; S-08 defines the real filter contract.
- **No AI auto-tag** (S-09), no proposals (S-04), no memory enrichment on completion (S-03) — `completed_at` is the timestamp those slices will read.
- **No per-domain goal lists** — domain pages stay placeholders; S-08 owns the unified view.
- **No pagination** — single-user scale, wrapped-collection contract keeps it additive later.
- **Not moving `openapi.yaml`** out of the change folder — that's `category-contract-guards`.
- **Not guarding the 11 LifeDomain codes in the spec** — also `category-contract-guards`; S-02 only guards its *own* new enums at introduction time (per the lessons.md rule).

## Implementation Approach

Three phases, each TDD-able and independently green: persistence + convention guards, then the API + contract, then the frontend. Backend follows the `ai/memory` + `auth` patterns file-for-file; the one novel backend element is the layer×horizon cross-field invariant, enforced three times at three depths — request DTO (`@AssertTrue` → 422), domain constructor/mutator (`IllegalArgumentException` — unreachable via API, guards future callers), and a DB CHECK constraint (unbypassable).

## Critical Implementation Details

- **422 vs 400 split**: bean-validation failures (blank content, over-length, `@AssertTrue` invariant) → **422** with a *generic* detail (the handler deliberately drops field messages, `ApiExceptionHandler.java:38-48`); an unknown enum literal (`layer: "WISH"`, bad `category_code`) fails Jackson deserialization → **400**. Tests must assert accordingly — don't expect field-level 422 details.
- **FR-019 ordering**: the `information_schema` sweep in `AccountDeletionIntegrationTest` picks up `goal.user_id` automatically once V6 exists — the FK must be `NO ACTION` (matching `V5:6-9`) or that test fails before any S-02 test runs. `GoalDataDeleter` must exist before account deletion with goals present can succeed.
- **Contract-anchor test placement**: backend suite, same as `AuthApiTest:274-297` — Vitest cannot read files above the Vite root without widening `server.fs.allow` (lessons.md records this).
- **`ddl-auto=validate` is the mapping proof**: any full `@SpringBootTest` boots Flyway + Hibernate validation, so entity↔V6 drift fails every test, not just the persistence ones.

## Phase 1: Persistence + convention guards

### Overview

The `goal` table, the `Goal` aggregate mapped to it, the repository, and the ArchUnit promotion of the `UserOwned` rule — all proven by persistence tests against Testcontainers PG 18.

### Changes Required:

#### 1. Migration

**File**: `backend/src/main/resources/db/migration/V6__create_goal.sql`

**Intent**: Create the `goal` table for both layers, expand-only, with the invariant and referential integrity in the schema.

**Contract**: `goal(id uuid PK, user_id uuid NOT NULL → app_user(id) NO ACTION, content varchar(500) NOT NULL, layer varchar(16) NOT NULL, horizon varchar(16) NULL, category_code varchar(32) NULL → category(code), completed_at timestamptz NULL, created_at timestamptz NOT NULL, updated_at timestamptz NOT NULL)`; indexes on `user_id` and `category_code` (FK columns aren't auto-indexed); header comment per house style. The layer×horizon invariant as a named constraint:

```sql
CONSTRAINT chk_goal_layer_horizon CHECK (
  (layer = 'GOAL'  AND horizon IS NOT NULL) OR
  (layer = 'DREAM' AND horizon IS NULL)
)
```

#### 2. Enums

**Files**: `backend/src/main/java/com/thedariusz/todoai/goal/GoalLayer.java`, `goal/GoalHorizon.java`

**Intent**: The two wire enums. `GoalLayer { GOAL, DREAM }`, `GoalHorizon { THIS_YEAR, FEW_MONTHS }` (FR-004: "ten rok / kilka miesięcy"). Javadoc on each names the spec (`openapi.yaml x-extensible-enum`) as the anchor and the contract-anchor test as the guard.

**Contract**: Enum constant names ARE the wire and column literals (`@Enumerated(STRING)`, snake_case JSON leaves enum values untouched).

#### 3. Aggregate

**File**: `backend/src/main/java/com/thedariusz/todoai/goal/Goal.java`

**Intent**: The aggregate root, modeled on `AiMemory` — first `@Enumerated` precedent, first entity FK to `category.code`.

**Contract**: `implements UserOwned`; UUID v7 id (`Style.VERSION_7`, never `Style.TIME`); `@Enumerated(STRING)` for `layer`/`horizon`/`category` (category typed as `LifeDomain`, column `category_code` — valid because `CategorySyncCheck` pins enum names to table codes); `MAX_CONTENT_LENGTH = 500` constant mirrored by `@Size` and the column width; nullable `completed_at` is the completion state (null = active). Constructor takes `(userId, content, layer, horizon, category)` and enforces the layer×horizon rule; mutators are intent-revealing — `update(content, layer, horizon, category)` (re-enforces the invariant — this is the dream↔goal conversion path), `complete(OffsetDateTime)`, `reopen()`. No setters.

#### 4. Repository

**File**: `backend/src/main/java/com/thedariusz/todoai/goal/GoalRepository.java`

**Intent**: Derived queries only, scoped by user per the `UserOwned` contract.

**Contract**: `JpaRepository<Goal, UUID>` with `findByUserIdOrderByCreatedAtDesc(UUID)`, `findByIdAndUserId(UUID, UUID)`, `deleteByUserId(UUID)`.

#### 5. ArchUnit promotion

**Files**: `backend/pom.xml`, `backend/src/test/java/com/thedariusz/todoai/security/UserOwnedConventionTest.java`

**Intent**: Add `com.tngtech.archunit:archunit-junit5` (test scope) and replace the hand-rolled N=1 check with the real rule its javadoc promised for S-02.

**Contract**: Rule: every `@Entity` class with a field mapped to a `user_id` column implements `UserOwned`. Must pass for `AiMemory` and `Goal`; a hypothetical violator fails.

#### 6. Persistence tests (write first)

**File**: `backend/src/test/java/com/thedariusz/todoai/goal/GoalPersistenceTest.java`

**Intent**: Prove the mapping, the CHECK constraint, and the category FK against real Postgres, following `AiMemoryRepositoryTest` (`@Import(TestcontainersConfiguration.class) @SpringBootTest`, no web env).

**Contract**: Round-trips a GOAL (with horizon + category) and a DREAM (no horizon, no category); asserts UUID v7 id and audit timestamps populate; asserts the DB rejects a DREAM with horizon and a GOAL without one when written via raw SQL/native query (bypassing the entity guard, proving the CHECK); asserts an FK violation for a nonexistent category code.

### Success Criteria:

#### Automated Verification:

- Persistence tests pass: `mvn test -Dtest=GoalPersistenceTest` (from `backend/`)
- ArchUnit rule passes: `mvn test -Dtest=UserOwnedConventionTest`
- Full backend suite green (proves V6 ↔ entity mapping via `ddl-auto=validate` and that `AccountDeletionIntegrationTest`'s FK sweep accepts `goal.user_id`): `mvn test`

#### Manual Verification:

- None — schema + mapping only; nothing user-visible yet.

**Implementation Note**: After completing this phase and all automated verification passes, proceed — no manual gate needed for Phase 1.

---

## Phase 2: API + contract

### Overview

The three endpoints, the first not-found-for-this-user 404, the FR-019 deleter, the spec extension, and the drift guard for the new enums.

### Changes Required:

#### 1. Request/response DTOs

**Files**: `backend/src/main/java/com/thedariusz/todoai/goal/GoalCreation.java`, `goal/GoalUpdate.java`, `goal/GoalResponse.java` (with nested/companion `GoalCollection`)

**Intent**: Records per the `auth/` DTO style (validation annotations, static `from(Goal)` factory on the response).

**Contract**: `GoalCreation(content, layer, horizon, category_code)`; `GoalUpdate` adds `completed` (boolean) — full-replace semantics so one PUT covers edit, conversion, complete, and un-complete. `GoalResponse(id, content, layer, horizon, category_code, completed_at, created_at, updated_at)`; collection wrapped as `{items: [...]}`. The cross-field invariant rides the existing 422 machinery via a class-level assertion on both request records:

```java
@AssertTrue // GOAL requires horizon; DREAM forbids it → 422 via MethodArgumentNotValidException
boolean isHorizonConsistentWithLayer() { ... }
```

#### 2. Service

**File**: `backend/src/main/java/com/thedariusz/todoai/goal/GoalService.java`

**Intent**: `@Service` with `@Transactional` methods; owns `CurrentUser.requireId()` scoping and the not-found decision. `list()`, `create(GoalCreation)`, `update(UUID, GoalUpdate)` — update loads via `findByIdAndUserId` (foreign id and nonexistent id are indistinguishable), applies `update(...)` then `complete(now)`/`reopen()` per the `completed` flag.

**Contract**: Throws `GoalNotFoundException` (new, in `goal/`) when the scoped lookup misses.

#### 3. Controller

**File**: `backend/src/main/java/com/thedariusz/todoai/goal/GoalController.java`

**Intent**: `@RequestMapping("/api/goals")`, package-private handler methods per house style. `GET /api/goals` → 200 collection; `POST /api/goals` → 201 + body; `PUT /api/goals/{id}` → 200 + body.

**Contract**: The three routes above — authenticated by default, CSRF header required on mutations, no security config change.

#### 4. 404 handler

**File**: `backend/src/main/java/com/thedariusz/todoai/auth/ApiExceptionHandler.java`

**Intent**: First domain 404: map `GoalNotFoundException` → Problem 404 with a generic detail (no existence leak — same body whether the goal is someone else's or nonexistent).

**Contract**: `@ExceptionHandler` following the existing 409 handler's shape; funnels through `handleExceptionInternal` for logging.

#### 5. FR-019 deleter

**File**: `backend/src/main/java/com/thedariusz/todoai/goal/GoalDataDeleter.java`

**Intent**: Implement `PerUserDataDeleter` (auto-discovered by `AccountDeletionService`), mirroring `AiMemoryDataDeleter`.

**Contract**: `deleteFor(userId)` → `goalRepository.deleteByUserId(userId)`.

#### 6. OpenAPI spec extension

**File**: `context/changes/account-and-auth/openapi.yaml`

**Intent**: Extend the authoritative spec in place: tag `goals`, paths `/goals` (get, post) and `/goals/{id}` (put), schemas `Goal`, `GoalCreation`, `GoalUpdate`, `GoalCollection`, a new shared `NotFound` response component, `x-extensible-enum` for `layer` and `horizon` (same open-ended pattern as category codes), XSRF parameter on mutations, snake_case properties, `readOnly` on server-set fields.

**Contract**: The `x-extensible-enum` values are the anchor the contract-anchor test reads; every operation lists at minimum 401 + 500, mutations add 403 + 422, PUT adds 404, POST/PUT add 400.

#### 7. API tests (write first)

**File**: `backend/src/test/java/com/thedariusz/todoai/goal/GoalApiTest.java`

**Intent**: REST Assured suite on `ApiTestBase` covering the full behavior matrix.

**Contract**: 401 anonymous; 201 create GOAL (horizon + category) and DREAM (neither); 422 GOAL-without-horizon and DREAM-with-horizon; 400 unknown `layer` and unknown `category_code` literals; GET returns own items only (two `newBrowser()` sessions); PUT edits content/category; PUT converts DREAM→GOAL (horizon appears); PUT `completed: true` sets `completed_at`, `completed: false` clears it; PUT foreign id and random id → identical 404s; account deletion with goals present → 204 and rows gone (extend `AccountDeletionIntegrationTest` flow or assert here).

#### 8. Contract-anchor test (write first)

**File**: `backend/src/test/java/com/thedariusz/todoai/goal/GoalApiTest.java` (dedicated test method, per `AuthApiTest:274-297`)

**Intent**: Apply the lessons.md rule at introduction time: one check spanning the boundary for the new wire enums.

**Contract**: Reads `openapi.yaml`, extracts the `layer`/`horizon` `x-extensible-enum` values, asserts they equal the backend enum constant names, and asserts each literal appears in the frontend goal type source (`frontend/src/pages/GoalsPage.tsx`). Renaming any copy goes red. (Until Phase 3 lands the frontend file, the frontend half of the assertion is written but the file ships in the same PR — the test is green only when both sides exist; acceptable inside one change.)

### Success Criteria:

#### Automated Verification:

- API + contract tests pass: `mvn test -Dtest=GoalApiTest`
- Account deletion still green with goals present: `mvn test -Dtest=AccountDeletionIntegrationTest`
- Full backend suite: `mvn test`

#### Manual Verification:

- None required — REST Assured exercises real HTTP on a random port; browser-level verification happens in Phase 3.

**Implementation Note**: The contract-anchor test's frontend assertion targets a Phase 3 file — if implementing strictly phase-by-phase, mark that one assertion pending until Phase 3, then confirm green.

---

## Phase 3: Frontend

### Overview

The `/cele` page: grouped lists, create form, per-item edit/complete, category picker from outlet context — plus the nav entry and docs updates.

### Changes Required:

#### 1. Route + nav

**Files**: `frontend/src/App.tsx`, `frontend/src/layout/AppLayout.tsx`

**Intent**: Add `<Route path="cele" element={<GoalsPage />} />` beside `domena/:code` (inherits auth gate, shell, and domains context); add a "Cele i marzenia" nav link in the shell above/beside the domain list.

**Contract**: Route path `/cele`; nav uses the same `NavLink` styling as domain links.

#### 2. Goals page

**File**: `frontend/src/pages/GoalsPage.tsx`

**Intent**: The whole S-02 UI in one screen, following the `AuthPage` idioms (uncontrolled `FormData` forms, `pending`/`error` state, `role="alert"`, Polish copy, constant copy tables).

**Contract**:
- Exports `type Goal = { id: string; content: string; layer: 'GOAL' | 'DREAM'; horizon: 'THIS_YEAR' | 'FEW_MONTHS' | null; category_code: string | null; completed_at: string | null; created_at: string; updated_at: string }` — these string literals are what the backend contract-anchor test greps for.
- Fetch: `useEffect` → `api<{ items: Goal[] }>('/goals')` with a `failed` flag (the `AppLayout` pattern).
- Layout: section "Cele długoterminowe" (active GOAL), section "Marzenia" (active DREAM); each with a native `<details><summary>Ukończone</summary>` block for completed entries.
- Create form: content input (`maxLength={500}` mirroring the server rule, with the convention comment), layer `<select>` (Cel / Marzenie), horizon `<select>` ("W tym roku" / "Najbliższe miesiące") required-and-visible only when layer is Cel, category `<select>` from `useOutletContext<Domain[]>()` with a "Bez kategorii" empty option.
- Per item: "Ukończ" button → PUT with `completed: true`; "Przywróć" (in completed section) → `completed: false`; "Edytuj" toggles an inline edit form (same fields as create, prefilled) → PUT full payload.
- After each successful mutation, refetch the list (no local cache bookkeeping — single-user scale).
- Category display on items: resolve `category_code` → `name_pl` via the outlet domains.

#### 3. Styling

**File**: `frontend/src/index.css`

**Intent**: A `/* Goals */` section at the end following the existing section-comment precedent; forms and buttons mostly inherit from element selectors.

**Contract**: Class names hang off a `.goals` section root.

#### 4. Frontend tests (write first)

**File**: `frontend/src/pages/GoalsPage.test.tsx`

**Intent**: Vitest + RTL with `vi.stubGlobal('fetch', ...)` and `renderWithAuth`, per `AppLayout.test.tsx` — reuse its 11-domain fixture for the picker.

**Contract**: Renders grouped sections from a stubbed GET; create submits POST `/api/goals` with the form payload and refetches; horizon select appears/disappears with layer choice; "Ukończ" PUTs `completed: true`; completed entries render inside `<details>`; "Przywróć" PUTs `completed: false`; API failure shows `role="alert"`; category picker lists the 11 domains + "Bez kategorii".

#### 5. Docs

**Files**: `context/foundation/data-model.md`, `context/foundation/roadmap.md`

**Intent**: Move `goal` from "Planned (not yet designed)" into the ER diagram + prose (one table, layer discriminator — supersedes the pre-decision `goal`/`dream` line); flip S-02 status to done with the completion note (the S-01 precedent) once merged.

**Contract**: `data-model.md` mermaid block gains the `goal` table with FK edges to `app_user` and `category`.

### Success Criteria:

#### Automated Verification:

- Frontend tests pass: `npm test` (from `frontend/`)
- Lint clean: `npm run lint`
- Typecheck + build: `npm run build`
- Full gate: `/check` (backend + frontend + lint + build)
- Contract-anchor test now fully green (frontend literals exist): `mvn test -Dtest=GoalApiTest`

#### Manual Verification:

- Live flow against the real backend (Vite dev + `mvn spring-boot:run` + compose Postgres): create a goal with horizon + category; create a dream; edit a dream into a goal (horizon field appears and is required); complete and un-complete an entry; refresh — everything persists; category shows its Polish name; anonymous visit to `/cele` bounces to login.
- Visual check of the collapsed "Ukończone" sections and the nav link in both light and dark scheme.

**Implementation Note**: After automated verification passes, pause for manual confirmation of the live flow before marking the change done and moving DEV-19 to In Review.

---

## Testing Strategy

### Unit/Persistence Tests:

- Entity invariant (constructor + `update`) — layer×horizon violations throw; CHECK constraint proves the DB backstop via native inserts.
- ArchUnit rule catches an `@Entity` with `user_id` not implementing `UserOwned`.

### Integration Tests:

- `GoalApiTest` (REST Assured, Testcontainers PG 18): the behavior matrix in Phase 2 §7 — auth, validation split (422 vs 400), isolation, conversion, completion round-trip, 404 indistinguishability, FR-019.
- Contract-anchor: spec ↔ backend enums ↔ frontend literals.

### Manual Testing Steps:

1. `docker compose up -d` (backend/), `mvn spring-boot:run`, `npm run dev` — log in.
2. Create goal "Przebiec półmaraton" (W tym roku, Zdrowie) and dream "Pojechać do Japonii" (bez kategorii).
3. Edit the dream → set layer Cel: horizon becomes required; save with "Najbliższe miesiące".
4. Complete the goal → moves under "Ukończone"; expand, "Przywróć" → returns to active.
5. Refresh the page — all state persists. Log out, visit `/cele` → redirected to login.

## Performance Considerations

Unparameterized GET + client-side grouping is deliberate at <1 GB / single-user scale. No new scheduled or repeated DB work — nothing threatens the Neon idleness rule (lessons.md).

## Migration Notes

V6 is expand-only (new table, no altered objects) — safe under image rollback by construction. No data backfill.

## References

- Change identity + aggregate decision: `context/changes/goals-and-dreams/change.md`
- Roadmap S-02: `context/foundation/roadmap.md:126-133`
- PRD: FR-004, FR-005, FR-007 (`context/foundation/prd.md`)
- Entity template: `backend/src/main/java/com/thedariusz/todoai/ai/memory/AiMemory.java`
- Isolation contract: `backend/src/main/java/com/thedariusz/todoai/security/UserOwned.java`, `security/CurrentUser.java`
- Contract-anchor precedent: `backend/src/test/java/com/thedariusz/todoai/auth/AuthApiTest.java:274-297`
- Form/test idioms: `frontend/src/pages/AuthPage.tsx`, `frontend/src/layout/AppLayout.test.tsx`
- Spec: `context/changes/account-and-auth/openapi.yaml`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Persistence + convention guards

#### Automated

- [x] 1.1 Persistence tests pass: `mvn test -Dtest=GoalPersistenceTest` — 1ffc7b0
- [x] 1.2 ArchUnit rule passes: `mvn test -Dtest=UserOwnedConventionTest` — 1ffc7b0
- [x] 1.3 Full backend suite green: `mvn test` — 1ffc7b0

### Phase 2: API + contract

#### Automated

- [x] 2.1 API + contract tests pass: `mvn test -Dtest=GoalApiTest` — d425d83
- [x] 2.2 Account deletion still green with goals present: `mvn test -Dtest=AccountDeletionIntegrationTest` — d425d83
- [x] 2.3 Full backend suite: `mvn test` — d425d83

### Phase 3: Frontend

#### Automated

- [x] 3.1 Frontend tests pass: `npm test`
- [x] 3.2 Lint clean: `npm run lint`
- [x] 3.3 Typecheck + build: `npm run build`
- [x] 3.4 Full gate: `/check`
- [x] 3.5 Contract-anchor test fully green: `mvn test -Dtest=GoalApiTest`

#### Manual

- [ ] 3.6 Live flow verified (create / edit / convert / complete / un-complete / persist / auth gate)
- [ ] 3.7 Visual check: collapsed "Ukończone" sections + nav link, light and dark
