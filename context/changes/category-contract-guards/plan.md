# Category Contract Guards Implementation Plan

## Overview

Three invariants about the 11 life domains are documented in prose and enforced by nothing, so the values can drift again exactly as they did in DEV-31. This plan turns each into a failing test, fixes a rollback-safety defect in the boot guard found while planning, and promotes `openapi.yaml` out of a change folder so the contract anchor stops living somewhere `/10x-archive` relocates.

## Current State Analysis

**What guards exist today** — four overlapping layers, none of which covers the pairing:

| Guard | What it catches |
| --- | --- |
| `CategorySyncCheck:35` | codes as a `Set`: missing, extra, renamed |
| `CategorySeedTest` (4 tests) | exactly 11 rows; codes match enum; `display_order` values are 1..11 unique; `name_pl` non-blank |
| `V1__create_category.sql:8` | `display_order NOT NULL UNIQUE`, `name_pl NOT NULL`, `code` PK |
| Flyway checksum | `V2` cannot be edited in place |

**What none of them covers.** Every check above is order-agnostic about *which code holds which order*. `displayOrderIsOneToElevenAndUnique` (`CategorySeedTest:46`) sorts the orders and asserts `1..11` — it never asserts `HEALTH == 1`. A `V6` swapping `HEALTH` and `INNER_GROWTH` passes all four layers; `CategoryController:30` sorts by `displayOrder`, so `GET /api/categories` reorders and the SPA nav silently follows. `AppLayout.tsx:13` already says so in its own comment: *"ordering live in the Flyway seed alone and are guarded by nothing."*

**The unenforced javadoc.** `LifeDomain.java:9-10` asserts "Declaration order matches the table's `display_order` (1..11)". Nothing tests it, and the same javadoc calls the enum "the source for the future AI `json_schema` enum (FR-008)".

**The unwatched anchor.** `openapi.yaml:340` carries `x-extensible-enum` under the comment `# in display_order, mirroring V2__seed_categories.sql`. Nothing compares them. This is the precise gap DEV-31 was filed for — the fix corrected the values and added no guard.

**The rollback defect.** `CategorySyncCheck:35` is `if (!dbCodes.equals(enumCodes)) throw`. Strict equality means a 12th domain added by an expand-only `V6` makes the *previous* image fail boot against the *new* schema. CLAUDE.md requires migrations be "backward-compatible / expand-only (safe under an image rollback)"; today that guarantee does not hold for this table.

### Key Discoveries

- **`lessons.md:29-36` already settled the rule**: "one artifact is the anchor (`openapi.yaml`) and at least one check must read the anchor and compare it to *both* implementations." Line 36 names these codes as "Still unguarded" — this plan is that entry's remedy.
- **A working precedent to copy**: `AuthApiTest:275-297` (`emitsTheReAuthUrnTheContractAndTheSpaBothHardcode`) extracts a value from a live 403, then asserts both `openapi.yaml` and `AccountMenu.tsx` contain it, via a `read(String path)` helper at `AuthApiTest:295`.
- **The backend suite already reaches across the repo**: `AuthApiTest:287` reads `../context/changes/account-and-auth/openapi.yaml`. The fragile path is live today, not hypothetical.
- **Verified copy surface** (`grep -rn INNER_GROWTH`): `LifeDomain.java`, `V2__seed_categories.sql`, `openapi.yaml`, `AppLayout.test.tsx`, `docs/index.html`, `data-model-current.drawio` + `.svg`, `data-model-target.drawio` + `.svg`.
- **The SPA fetches at runtime** (`AppLayout.tsx:20` → `api('/categories')`), so there is no hardcoded production list. Only the *test fixture* (`AppLayout.test.tsx:10-21`) copies codes, labels and order.
- **SnakeYAML 2.6 is already on the compile classpath** transitively via Spring Boot (`mvn dependency:tree -Dincludes='org.yaml:*'`). No new dependency is needed to parse the anchor.

## Desired End State

A change to any one of `LifeDomain`, the Flyway seed, `openapi.yaml`, or the SPA fixture that is not carried to the others fails a test. Adding a 12th domain via an expand-only migration boots cleanly on both the new and the previous image. `openapi.yaml` lives at a path that survives archiving.

Verify by making each of these edits in a scratch commit and confirming a red test, then reverting: reorder two constants in `LifeDomain`; change one code in the anchor's `x-extensible-enum`; change one `name` in the SPA fixture.

## What We're NOT Doing

- **Not guarding `docs/index.html` or the `.drawio`/`.svg` diagrams.** Asserting against exported SVG is brittle and will red on unrelated re-exports. These stay manual; `grep -rn INNER_GROWTH` remains the checklist in `lessons.md`.
- **Not moving `name_pl` or `display_order` into the `LifeDomain` enum.** Polish display strings stay in the seed — pulling them into Java fights the future i18n story and makes the seed the derived copy.
- **Not rewriting completed change records.** `context/changes/api-contract-alignment/plan.md` keeps its references to the old spec path; it documents what was true when it ran.
- **Not adding a 12th domain.** The rollback fix makes that safe; actually doing it is separate work.
- **Not touching `CategoryController`, the `/api/categories` response shape, or any frontend production code.**

## Implementation Approach

Phase 1 is a pure move so that Phases 2 and 3 are written against the final path once. Phase 2 is backend-local and needs no cross-project reads. Phase 3 adds the one genuinely new thing — a test that reads three sources and compares them — modelled directly on the accepted `AuthApiTest` precedent.

## Critical Implementation Details

**Parse the anchor as YAML, do not grep it.** `api-contract-alignment/plan.md:199` records the trap: `grep -A11 'x-extensible-enum'` looks right but the twelfth line is `example: HEALTH`, which a naive code-shaped regex reports as a spurious twelfth code. SnakeYAML is on the classpath; navigate to `components.schemas.Category.properties.code.x-extensible-enum` and read the list.

**Phase ordering is load-bearing.** Phase 3's test hardcodes the anchor path. If Phase 1 has not landed, that path is written twice and the second write is a silent merge hazard.

**The guard direction is asymmetric, deliberately.** At boot, `enum ⊆ db` (a DB row the enum doesn't know about is tolerated — that's the rolled-back image). In tests, exact equality is retained, because the test runs against the seed where both sides are deterministic. Do not "simplify" these into one shared helper; they are different assertions for different reasons.

**Vitest cannot own the fixture guard.** `lessons.md:35` records that Vitest cannot read a file above the Vite root (`Denied ID`) without widening `server.fs.allow`, a dev-server permission judged not worth opening for a test. The fixture assertion therefore lives in the backend suite alongside the anchor assertion.

---

## Phase 1: Promote the contract anchor

### Overview

Move `openapi.yaml` from a completed change's folder to `context/foundation/`, where the other living reference documents already sit. No content change.

### Changes Required:

#### 1. The spec file

**File**: `context/changes/account-and-auth/openapi.yaml` → `context/foundation/openapi.yaml`

**Intent**: The anchor is a living contract that outlives the change that introduced it; `/10x-archive` is designed to relocate change folders, which would break every test that reads it.

**Contract**: `git mv`, byte-identical content. New canonical path is `context/foundation/openapi.yaml`.

#### 2. The live code reference

**File**: `backend/src/test/java/com/thedariusz/todoai/AuthApiTest.java`

**Intent**: Repoint the existing cross-boundary assertion at the new path.

**Contract**: Line 287's argument to `read(...)` becomes `../context/foundation/openapi.yaml`. The `read` helper at line 295 is unchanged.

#### 3. The "authoritative contract" pointers

**Files**: `context/changes/account-and-auth/plan.md` (lines 21, 694), `context/changes/account-and-auth/plan-brief.md` (line 5)

**Intent**: These three lines tell a reader where the authoritative contract lives; left stale they point at a file that is no longer there.

**Contract**: Path string only. Do not restate or re-describe the contract.

### Success Criteria:

#### Automated Verification:

- The anchor exists at its new path and nowhere else: `test -f context/foundation/openapi.yaml && test ! -f context/changes/account-and-auth/openapi.yaml`
- No live code still references the old path: `! grep -rn "account-and-auth/openapi.yaml" backend/src frontend/src`
- The anchor still parses: `ruby -ryaml -e "YAML.safe_load(File.read('context/foundation/openapi.yaml')); puts 'yaml OK'"`
- The existing cross-boundary test passes: `mvn test -Dtest=AuthApiTest`

#### Manual Verification:

- The three prose pointers resolve to the moved file when clicked in an editor.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation before proceeding.

---

## Phase 2: Pairing assertion and rollback-safe boot check

### Overview

Enforce the code↔`display_order` pairing in the test suite, and relax the runtime guard so an expand-only migration is safe under an image rollback.

### Changes Required:

#### 1. The boot guard

**File**: `backend/src/main/java/com/thedariusz/todoai/category/CategorySyncCheck.java`

**Intent**: Strict equality makes the previous image unbootable against a schema that gained a 12th domain, silently voiding the expand-only rollback guarantee in CLAUDE.md. Tolerate a DB superset; still fail when the enum knows a code the table lacks, which is the genuine seed-drift case.

**Contract**: The predicate becomes `enumCodes ⊆ dbCodes` rather than `dbCodes.equals(enumCodes)`. The thrown `IllegalStateException` message must name the *missing* codes specifically, not just dump both sets — the operator needs to know which side is behind. The class javadoc must state why the check is deliberately one-directional, so a future reader does not "restore" the symmetry.

#### 2. The pairing test

**File**: `backend/src/test/java/com/thedariusz/todoai/category/CategorySeedTest.java`

**Intent**: Close the gap where a migration can permute `display_order` and pass every existing test. This single assertion also enforces `LifeDomain`'s javadoc invariant, which nothing tests today.

**Contract**: A new test asserting that codes ordered by `display_order` equal `LifeDomain.values()` in declaration order. Retain `displayOrderIsOneToElevenAndUnique` — the new test proves the pairing, the old one proves contiguity, and neither implies the other. Retain `codesMatchLifeDomainEnum` as the exact-equality counterpart to the now-relaxed runtime check.

#### 3. The enum's javadoc

**File**: `backend/src/main/java/com/thedariusz/todoai/category/LifeDomain.java`

**Intent**: Declaration order becomes load-bearing and test-enforced. A reader reordering constants for tidiness needs to know that is now a breaking change requiring a paired migration.

**Contract**: Extend the existing javadoc at lines 9-10. Name the test that enforces it.

### Success Criteria:

#### Automated Verification:

- Category tests pass: `mvn test -Dtest=CategorySeedTest`
- The pairing test genuinely fails when the invariant breaks: temporarily swap two constants in `LifeDomain`, confirm red, revert
- The boot guard still fails on real seed drift: temporarily add a constant to `LifeDomain` with no matching row, confirm the context fails to start, revert
- Full backend suite passes: `mvn test`

#### Manual Verification:

- The `IllegalStateException` message, when triggered, names the missing codes clearly enough to act on without reading the source.

**Implementation Note**: After completing this phase and all automated verification passes, pause here for manual confirmation before proceeding.

---

## Phase 3: Cross-boundary contract guard

### Overview

One test that reads the anchor and the SPA fixture and holds both against `LifeDomain` and the seeded table — the check `lessons.md:33` requires and line 36 records as missing.

### Changes Required:

#### 1. The contract test

**File**: `backend/src/test/java/com/thedariusz/todoai/category/CategoryContractTest.java` (new)

**Intent**: Give the duplicated code list the single spanning guard the lessons register demands, so no side can be edited alone and stay green.

**Contract**: A `@SpringBootTest` with `@Import(TestcontainersConfiguration.class)`, matching `CategorySeedTest`'s setup (it needs the seeded table for the label assertion). Three assertions:

1. The anchor's `x-extensible-enum` list equals `LifeDomain.values()` in declaration order. Parse with SnakeYAML at `components.schemas.Category.properties.code.x-extensible-enum` — see Critical Implementation Details on why not to grep.
2. The SPA fixture's codes, in file order, equal `LifeDomain.values()` in declaration order.
3. The SPA fixture's `name` values match the seeded rows' labels. (The wire field was `name_pl`
   when this plan was written; the DEV-28 contract pass renamed it to `name` — the column is still
   `name_pl`, so the guard compares the seed's `name_pl` against the fixture's `name`.) This is what stops the frontend suite asserting labels that no longer exist in production.

Paths are relative to the backend module, as the `AuthApiTest:295` precedent establishes: `../context/foundation/openapi.yaml`, `../frontend/src/layout/AppLayout.test.tsx`. Each assertion carries an `.as(...)` description naming *which* file must change to fix it — a bare list-inequality failure is unreadable across three sources.

#### 2. The lessons register

**File**: `context/foundation/lessons.md`

**Intent**: Line 36 currently reads "Still unguarded — the `LifeDomain` codes" and enumerates the manual checklist. Two of those sites are now automated; leaving the entry as-is makes the register wrong in the direction that matters (it would understate coverage and overstate the manual burden).

**Contract**: Rewrite that bullet to name `CategoryContractTest` as the guard, and narrow the remaining manual list to `docs/index.html` and the two `.drawio` + `.svg` pairs. Keep `grep -rn INNER_GROWTH` as the checklist for what stays manual. Append-only file — revise this bullet in place, do not add a competing entry.

### Success Criteria:

#### Automated Verification:

- The new test passes: `mvn test -Dtest=CategoryContractTest`
- It fails on anchor drift: temporarily change one code in `context/foundation/openapi.yaml`, confirm red, revert
- It fails on fixture drift: temporarily change one `name` in `src/test/domains.ts`, confirm red, revert
- Full gate passes: `/check`

#### Manual Verification:

- Each failure message names the file to edit without needing to open the test source.
- `lessons.md`'s remaining manual checklist matches what `grep -rn INNER_GROWTH` actually returns.

---

## Testing Strategy

### Unit Tests

- Pairing: codes ordered by `display_order` vs `LifeDomain` declaration order (Phase 2).
- Contiguity: `display_order` is exactly 1..11, unique (existing, retained).
- Exact seed equality: codes vs enum as sets (existing, retained — the strict counterpart to the relaxed runtime check).

### Integration Tests

- Boot guard tolerates a DB superset (rolled-back image) and rejects an enum superset (seed drift).
- Cross-boundary: anchor and SPA fixture vs enum and seeded labels (Phase 3).

### Manual Testing Steps

1. Reorder two `LifeDomain` constants → `mvn test` goes red on the pairing test. Revert.
2. Change one code in `context/foundation/openapi.yaml` → `CategoryContractTest` red. Revert.
3. Change one `name` in `src/test/domains.ts` → `CategoryContractTest` red, and note that `npm test` alone stays **green** — that contrast is the whole point of the guard.

## Performance Considerations

None. The boot check's single `findAll()` over 11 rows is unchanged in cost and still runs once per startup, which matters only because `lessons.md:16` requires nothing touch the DB more often than Neon's ~5-minute autosuspend window — a one-shot startup read does not.

## Migration Notes

No schema change. The boot-guard relaxation is what *permits* a future expand-only 12th-domain migration to be rolled back safely; it changes no existing data and requires no migration of its own.

## References

- Lessons register (the rule this implements): `context/foundation/lessons.md:29-36`
- Precedent cross-boundary test: `backend/src/test/java/com/thedariusz/todoai/AuthApiTest.java:275-297`
- Prior change that fixed the values without adding a guard: `context/changes/api-contract-alignment/plan.md`
- The grep trap this plan avoids: `context/changes/api-contract-alignment/plan.md:199`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Promote the contract anchor

#### Automated

- [ ] 1.1 Anchor exists at new path and nowhere else
- [ ] 1.2 No live code references the old path
- [ ] 1.3 Anchor still parses as YAML
- [ ] 1.4 `mvn test -Dtest=AuthApiTest` passes

#### Manual

- [ ] 1.5 The three prose pointers resolve to the moved file

### Phase 2: Pairing assertion and rollback-safe boot check

#### Automated

- [ ] 2.1 `mvn test -Dtest=CategorySeedTest` passes
- [ ] 2.2 Pairing test goes red on a swapped enum order, then reverts green
- [ ] 2.3 Boot guard still fails on real seed drift (enum superset)
- [ ] 2.4 Full backend suite passes

#### Manual

- [ ] 2.5 The exception message names the missing codes actionably

### Phase 3: Cross-boundary contract guard

#### Automated

- [ ] 3.1 `mvn test -Dtest=CategoryContractTest` passes
- [ ] 3.2 Test goes red on anchor drift, then reverts green
- [ ] 3.3 Test goes red on fixture drift, then reverts green
- [ ] 3.4 `/check` passes

#### Manual

- [ ] 3.5 Failure messages name the file to edit
- [ ] 3.6 `lessons.md` manual checklist matches `grep -rn INNER_GROWTH`
