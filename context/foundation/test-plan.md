# Test Plan

> Phased test rollout for this project. Strategy is frozen at the top
> (§1–§5); cookbook patterns at the bottom (§6) fill in as phases ship.
> Read before writing any new test.
>
> Refresh: re-run `/10x-test-plan --refresh` when stale (see §8).
>
> Last updated: 2026-08-24

## 1. Strategy

Tests follow three non-negotiable principles for this project:

1. **Cost × signal.** The cheapest test that gives a real signal for the
   risk wins. Do not promote to e2e because e2e "feels safer." Do not put a
   vision model on top of a deterministic visual diff that already catches
   the regression.
2. **User concerns are first-class evidence.** Risks anchored in "the
   team is worried about X, and the failure would surface somewhere in
   <area>" carry the same weight as PRD lines or hot-spot data.
3. **Risks are scenarios, not code locations.** This plan documents *what
   could fail* and *why we believe it's likely* — drawn from documents,
   interview, and codebase *signal* (churn, structure, test base). It does
   NOT claim to know which line owns the failure. That knowledge is
   produced by `/10x-research` during each rollout phase. If the plan and
   research disagree about where the failure lives, research is the
   ground truth.

Hot-spot scope used for likelihood weighting: `backend/src`, `frontend/src`
(9 commits/30d, 24/90d — the repo squash-merges, so one commit is roughly
one shipped slice).

**This plan is retrospective.** The suite came first: 25 backend test
classes, 8 frontend suites, 12 docs assertions, all gated per PR. §2 names
the risks that suite was already defending, and §3 maps them onto the
changes that shipped them. Nothing here asks for a test written on spec —
where a surface is genuinely unguarded, §2 says so and stops.

## 2. Risk Map

The top failure scenarios this project must protect against, ordered by
risk = impact × likelihood. Risks are failure scenarios in user / business
terms, not test names. The Source column cites the *evidence that surfaced
this risk* — never a specific file as "where the failure lives" (that is
research's job, see §1 principle #3).

| # | Risk (failure scenario) | Impact | Likelihood | Source (evidence — not anchor) |
|---|--------------------------|--------|------------|--------------------------------|
| 1 | A logged-in user reads or deletes another user's goals or AI memory | High | High | PRD §Guardrails (personal memory is intimate; privacy binds from MVP); interview Q1; hot-spot dir `backend/…/goal` (15 commits/30d) |
| 2 | A gated route, mutation, or error response hands an anonymous or wrong-session caller more than it should | High | High | PRD §Access Control (no anonymous mode); interview Q1, Q3; hot-spot dir `backend/…/auth` (18 commits/30d — the top scope) |
| 3 | A write reports success but is not persisted, or the interface claims a state the server does not hold | High | Medium | PRD §NFR ("no saved action disappears silently"; 100% recovery after a crash); interview Q1, Q3; hot-spot dirs `frontend/src/auth` + `frontend/src/pages` (23 commits/30d combined) |
| 4 | A migration or mapping drift stops the application booting — including on the rollback path, where the old image meets the new schema | High | Medium | interview Q3; `context/changes/category-contract-guards/change.md` (defect recorded, parked by roadmap decision); hot-spot dir `backend/src/main/resources/db/migration` (6 commits/90d) |
| 5 | Specification, Java, and TypeScript disagree about a wire literal; every suite stays green while the user is shown the wrong thing | Medium | High | `lessons.md` §"A contract value duplicated across the stack needs one guard that spans the boundary" — DEV-31 is this failure, already lived; interview Q1 |
| 6 | The metered database never sleeps, so idle time quietly raises the bill with nothing to alert on | Medium | Medium | `lessons.md` §"Let a scale-to-zero database actually sleep" plus its 2026-07-22 correction; interview Q3 |
| 7 | Stored, user-influenced content carries instructions into an LLM prompt | High | Low | `lessons.md` §"Sanitize stored content before injecting it into an LLM prompt"; roadmap §Deadline plan — no production LLM caller exists yet, so this rises to High when S-04b lands (scheduled 09-03) |

Risks #1 and #2 are the abuse lens: #1 is ownership (does the endpoint check
that this resource is *yours*, not merely that you are logged in), #2 covers
authentication bypass, untrusted input, and disclosure through error bodies.

### Risk Response Guidance

| Risk | What would prove protection | Must challenge | Context `/10x-research` must ground | Likely cheapest layer | Anti-pattern to avoid |
|------|-----------------------------|----------------|--------------------------------------|-----------------------|-----------------------|
| #1 | Two accounts exist; everything the second one asks about the first's data is answered identically to data that never existed — no 403, no differing body, no timing tell | That a scoped read implies a scoped write. They are separate derived queries and each must be asserted | Which repository surface callers can reach at all, not only which one they use today; the erasure path (FR-019) | integration (real HTTP, two sessions) | Asserting the owner's happy path and calling isolation proven; using one fixture row, which cannot see over-deletion |
| #2 | An anonymous or stale-token caller is refused *and* the refusal discloses nothing about why | That a 401 and a 403 are interchangeable; that the CSRF filter runs where you assume in the chain | Filter ordering, session rotation on login, which handler renders which failure | integration (real HTTP) | Mocking the filter chain — CSRF double-submit is trivially satisfied in a mock and broken in a browser |
| #3 | After a failed write or a failed refetch, the screen and the banner agree with the server — no message claims a refresh that did not happen | That an awaited promise rethrows; a two-argument `.then` handles the rejection and the await can never throw | Where the write is confirmed, what the refetch does on failure, who owns the banner | frontend unit (RTL) + persistence round-trip | Asserting the happy path only; copying the expected value out of the component under test |
| #4 | A cold boot against a migrated database validates every mapping, and the previous image still boots against the new schema | That "migrations pass" means "the old image still starts" — expand-only is a property of the pair, not of the migration | Startup order (Flyway before validation), what fails fast at boot and what fails at first request | persistence (Testcontainers, real Postgres) | Testing the migration in isolation from the mappings it has to satisfy |
| #5 | Renaming a literal on any one side turns a test red, because one check reads the specification and compares it to both implementations | That each side testing its own copy is coverage — it is exactly how both stay green while disagreeing | Which literals are duplicated and where the anchor lives | contract test that parses the specification | An inventory or checklist that must be updated by hand; it is stale the day it is written |
| #6 | Configuration binding proves the pool drains and nothing pings the database on a timer | That defaults are safe — HikariCP's keepalive default alone defeats autosuspend | Which components hold connections, what runs on a schedule | property-binding unit test | Measuring the bill instead of asserting the setting |
| #7 | Stored content reaching a prompt is fenced as data, so a crafted payload is not read as instruction | That the renderer is the only seam once more callers exist | Every path from persisted user-influenced content to an LLM request | unit test at the render boundary | Writing this before S-04 has a caller — there is nothing to protect yet |

### Coverage — which suite defends which risk

Every risk above is defended by tests that already exist. Paths are the
mapping this document owes; they are not claims about where a failure lives.

| Risk | Defended by |
|------|-------------|
| #1 | `security/UserOwnedConventionTest` (all 3 — the structural rule, its self-check, and the repository surface) · `GoalApiTest.listsOnlyTheCallersOwnGoals`, `.answersIdenticallyForAForeignGoalAndAGoalThatNeverExisted`, `.deletesTheCallersOwnGoal`, `.erasesGoalsWhenTheAccountIsDeleted` · `goal/GoalPersistenceTest.scopesReadsToTheOwner` · `account/AccountDeletionIntegrationTest.scopedFinderReturnsOnlyTheOwnersMemory`, `.everyUserScopedTableIsProtectedByARestrictingForeignKey` |
| #2 | `AuthApiTest` (27 methods — CSRF on every verb, session rotation, HttpOnly/SameSite, generic 401, no detail leak on 500) · `SecurityGatewayApiTest` (all 3) · `security/ProblemDetailsSecurityHandlerTest` (all 4) · `security/CurrentUserTest` · `GoalApiTest.refusesAnonymousAccess`, `.deniesADeleteWithoutACsrfTokenOrWithoutASessionAndKeepsTheEntry` · `CategoryApiTest.isGatedBehindAuthentication` · `auth/ProtectedRoute.test.tsx`, `auth/AuthProvider.test.tsx`, `api/client.test.ts` |
| #3 | `pages/GoalsPage.test.tsx` (19 — including `does not claim the list was refreshed when the refetch failed too`, `keeps what the user typed when the save fails`, `records the failure, so a save that breaks in production is not invisible`) · `api/client.test.ts` · `auth/AccountMenu.test.tsx` (`reports a failed logout instead of pretending the session ended`) · `goal/GoalPersistenceTest.completionIsATimestampThatSurvivesTheRoundTripAndCanBeCleared` · `GoalApiTest.keepsTheOriginalCompletionMomentWhenACompletedEntryIsEdited` · `account/AccountDeletionIntegrationTest.refusesToReportSuccessWhenThereIsNoSuchAccount` · `auth/RegistrationServiceTest` (both) |
| #4 | `category/CategorySeedTest` (all 4) · `goal/GoalPersistenceTest.theDatabaseRejectsInconsistentTimeFieldsEvenWhenTheEntityGuardIsBypassed`, `.theDatabaseRejectsACategoryCodeThatIsNotASeededLifeDomain` · `account/AccountDeletionIntegrationTest.theSchemaActuallyHasUserScopedTablesToProtect`, `.deletingAUserWithDataLeftBehindFailsOnTheForeignKey` · `ai/memory/AiMemoryRepositoryTest` · every `@SpringBootTest` boots Flyway plus `ddl-auto=validate` against a real Postgres, so a mapping drift fails the whole suite |
| #5 | `GoalApiTest.publishesTheWireEnumsTheContractAnchors` (parses the specification and the frontend union, set-compares both against the backend enums) · `AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode` · `category/CategorySeedTest.codesMatchLifeDomainEnum` (table ↔ enum only — see the gap below) · `docs/index.test.mjs` (12 assertions holding the documentation page against the implemented product) |
| #6 | `DataSourcePoolPropertiesTest` (all 3 — drain to zero, keepalive off, bounded pool) · `ai/MemoryPropertiesTest`, `ai/LlmPropertiesTest` (fail-fast binding) |
| #7 | `ai/memory/AiMemoryRendererTest` (9 — determinism, bounded episode window, `renderedBlockDropsIntoAnLlmMessageUnchanged`) · `ai/SpringAiLlmClientTest.everyFreeTextRequestCarriesNoTrainingProviderRouting`, `.structuredRequestAlsoCarriesNoTrainingProviderRouting` |

### Known uncovered surfaces

Verified, not inferred. None of these leaves a risk *uncovered*, so none
justifies a test written ahead of the change that needs it.

- **`AiMemoryRepository` still extends `JpaRepository`** — unscoped
  `findById` / `findAll` / `deleteById` sit on the surface of the aggregate
  holding the most personal data. `UserOwnedConventionTest` narrows
  `GoalRepository` only, and says so deliberately. Closing this is a
  production change (narrow to the bare `Repository` marker), not a test.
  Risk #1.
- **The 11 life-domain codes are unguarded across the boundary** — they are
  copied into the specification, the documentation page, both data-model
  diagrams and their exports, and a frontend fixture, and only the table ↔
  enum pair is checked. §3 Phase 6 owns this; `lessons.md` records it. Risk
  #5, and risk #4 through the boot-time check.
- **No optimistic locking anywhere, and no handler maps a stale-state
  failure** — two concurrent deletes of the same entry surface as a 500
  rather than a second 404. Single-user scale makes this Low likelihood;
  it is named so a future reader does not mistake the omission for
  coverage. Risk #3.
- **Production has never been verified end-to-end** — only
  `/actuator/health` is probed. Parked to 09-11 by roadmap decision, as
  manual work rather than a gate. Risk #4.

## 3. Phased Rollout

Each row is a discrete rollout phase that will open its own change folder
via `/10x-new`. Status moves left-to-right through the values below; the
orchestrator updates Status as artifacts appear on disk.

Because this plan is retrospective, five of six phases point at changes
that already shipped, and their Status is read from each folder's own
Progress section rather than assumed. The live prioritisation surface is
Phase 6.

| # | Phase name | Goal (one line) | Risks covered | Test types | Status | Change folder |
|---|------------|------------------|---------------|------------|--------|----------------|
| 1 | Per-user isolation + auth surface | Prove a session reaches only its owner's data, and that every refusal discloses nothing | #1, #2 | integration, unit, structural | complete | `context/changes/account-and-auth/` |
| 2 | Goal invariants + wire-contract anchor | Prove the layer × horizon rule and per-user scoping hold on every verb, with the specification as the anchor | #1, #3, #5 | integration, persistence, contract | complete | `context/changes/goals-and-dreams/` |
| 3 | Schema, erasure, and cost guards | Prove migrations seed truthfully, erasure is total, and the pool lets the database sleep | #3, #4, #6 | persistence, property-binding | complete | `context/archive/2026-06-13-persistence-baseline/` |
| 4 | Problem-JSON contract alignment | Prove every error path renders RFC 9457 and the client can tell two 403s apart | #3, #5 | integration, frontend unit | complete | `context/changes/api-contract-alignment/` |
| 5 | Quality-gates wiring | Lock the floor: both suites, lint, build, docs, and secret scan on every pull request | cross-cutting | gates | implementing | `context/changes/ci-pipeline/` |
| 6 | Category contract guards | Close the one drift the anchor does not span — the 11 life-domain codes, and the boot check that breaks image rollback | #4, #5 | contract | planned | `context/changes/category-contract-guards/` |

## 4. Stack

The classic test base for this project. AI-native tools (if any) carry a
`checked:` date so future readers can see which lines need re-verification.

| Layer | Tool | Version | Notes |
|-------|------|---------|-------|
| backend unit | JUnit 5 + AssertJ | via Boot 4.1.0 | Domain and value-object tests; no Spring context |
| backend integration / API | REST Assured + `@SpringBootTest` (random port) | 6.0.1 | Real HTTP through the real filter chain. Preferred over MockMvc: the CSRF double-submit is easy to satisfy accidentally in a mock. Shared client in `ApiTestBase` |
| backend persistence | Testcontainers + PostgreSQL 18 | Boot-managed | Flyway plus `ddl-auto=validate` on every boot, so mapping drift fails the suite |
| structural / architecture | ArchUnit | 1.4.1 | Derives per-user aggregates from the schema mapping instead of enumerating them |
| frontend unit / component | Vitest + Testing Library (jsdom) | 4.1 / 16.3 | `globals: true`, setup in `src/test/setup.ts`, `restoreMocks` and `unstubGlobals` run *before* each test |
| documentation | `node --test` | Node 22+ | 12 assertions holding `docs/index.html` against the implemented product |
| e2e | none — deliberate | — | See §7. Not a gap; a decision |

**Stack grounding tools (current session):**
- Docs: Context7 MCP — available; used earlier in this project to verify Vitest `restoreMocks` / `unstubGlobals` timing against the configuration reference; checked: 2026-08-24
- Search: WebSearch / WebFetch — available, not needed; every version above came from `backend/pom.xml` and `frontend/package.json`; checked: 2026-08-24
- Runtime/browser: Playwright MCP and Chrome MCP — both available in session, deliberately unused as a test layer (§7); checked: 2026-08-24
- Provider/platform: Linear MCP and the GitHub CLI — available; used for issue state and pull-request gating, not as a test layer; checked: 2026-08-24

## 5. Quality Gates

The full set of gates that must pass before a change reaches production.

| Gate | Where | Required? | Catches |
|------|-------|-----------|---------|
| backend suite (`mvn test`) | local + CI (`backend.yml`) | required | logic, contract, isolation, and schema regressions |
| frontend lint + typecheck + build | local + CI (`frontend.yml`) | required | type drift, unused symbols, build breakage |
| frontend suite (`npm test`) | local + CI (`frontend.yml`) | required | interface state and error-copy regressions |
| documentation test | CI (`repo-checks.yml`) | required | documentation page drifting from the implemented product |
| shell lint + script self-tests | CI (`repo-checks.yml`) | required | breakage in the review and gate scripts |
| dependency scan (fixable HIGH/CRITICAL) | CI (`backend.yml`, `frontend.yml`) | required | known-vulnerable dependencies |
| secret scan | CI (`repo-checks.yml`) | required | credentials committed by accident |
| AI security review | CI (`ai-review.yml`) | required | security findings on the diff |
| production end-to-end verification | manual | planned — 09-11 | environment-specific failures; see §2 uncovered surfaces |

## 6. Cookbook Patterns

How to add new tests in this project. Every entry names a reference test
that already exists — read it before writing a new one.

### 6.1 Adding a backend unit test

- **Location**: `backend/src/test/java/com/thedariusz/todoai/<package>/` — mirrors the production package.
- **Naming**: `<ClassUnderTest>Test.java`; methods read as sentences (`rejectsBlankPasswordHash`).
- **Reference test**: `user/EmailTest.java`, `auth/Utf8ByteLengthTest.java`.
- **Run locally**: `mvn test -Dtest=EmailTest` from `backend/`.

### 6.2 Adding a backend persistence test

- **Location**: same package as the aggregate.
- **Policy**: real Postgres via Testcontainers — `@Import(TestcontainersConfiguration.class)` plus `@SpringBootTest`. Never an in-memory database: the invariants worth testing are database CHECK constraints and foreign keys, which H2 does not reproduce.
- **Reference test**: `goal/GoalPersistenceTest.java` — asserts the entity guard *and* that the database still refuses when the guard is bypassed.
- **Run locally**: `mvn test -Dtest=GoalPersistenceTest` from `backend/`.

### 6.3 Adding a test for a new API endpoint

- **Test type**: integration over real HTTP. Extend `ApiTestBase`, which carries cookies and the CSRF token exactly as the browser client does.
- **Pattern**: assert the response *and* the side effect — a delete is proven by the follow-up read, not by its 204. Give the fixture a second row so over-deletion cannot pass.
- **Reference test**: `GoalApiTest.java`; for authentication and session behaviour, `AuthApiTest.java`.
- **Run locally**: `mvn test -Dtest=GoalApiTest#deletesTheCallersOwnGoal` from `backend/`.
- **Also required for a per-user endpoint**: the isolation pair — the owner's happy path, and a foreign identifier answered identically to one that never existed.

### 6.4 Adding a structural (convention) test

- **When**: a convention now spans more than one aggregate, and the next one added will forget it.
- **Pattern**: derive the population from the schema mapping rather than listing known classes, and prove the rule has teeth against a deliberate violator kept outside the scanned package.
- **Reference test**: `security/UserOwnedConventionTest.java` with the `archfixture/LeakyEntity` violator.

### 6.5 Adding a contract-anchor test

- **When**: a literal is hardcoded on more than one side of the wire — a `type` URN, an enumeration code, a header or cookie name.
- **Pattern**: one check reads the anchor (`context/foundation/openapi.yaml`) and compares it against *both* implementations. Parse the frontend rather than searching it: a text search stays green after the field is widened to `string`.
- **Where it lives**: the backend suite. Only it has a running server, and Vitest cannot read a file above the Vite root without widening `server.fs.allow`.
- **Reference test**: `GoalApiTest.publishesTheWireEnumsTheContractAnchors`, `AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode`.

### 6.6 Adding a frontend test

- **Location**: co-located as `<Component>.test.tsx`.
- **Policy**: drive the component the way a user does (Testing Library queries by role and label); stub only `fetch`, never internal modules. Shared setup is `src/test/setup.ts`; helpers are `src/test/auth.tsx` and `src/test/domains.ts`. No per-file teardown — `restoreMocks` and `unstubGlobals` run before each test.
- **Reference test**: `pages/GoalsPage.test.tsx`.
- **Run locally**: `npx vitest run src/pages/GoalsPage.test.tsx -t "name"` from `frontend/`.
- **Assert the failure path too**: what the banner says when the write fails, and that it does not claim something the server never confirmed.

### 6.7 Running the whole gate

`/check` runs backend tests, frontend tests, lint, and build in one pass.
The documentation test is separate: `node --test docs/index.test.mjs` from
the repository root.

## 7. What We Deliberately Don't Test

Exclusions agreed during the rollout (Phase 2 interview, Q5). Future
contributors should respect these unless the underlying assumption changes.

- **End-to-end / Playwright** — the integration suite drives real HTTP through the real filter chain, and the frontend suite drives real components; a browser layer on top would re-cover both at several times the cost and flakiness. Re-evaluate if a failure ever escapes both suites at their seam. (Source: Phase 2 interview Q5; roadmap §Ryzyka records the same decision.)
- **Live AI-provider calls in CI** — `ai/OpenRouterLiveTest` exists and stays opt-in. Running it on every pull request would spend money and make the gate depend on a third party's availability. Re-evaluate if a provider contract change ever breaks production silently. (Source: Phase 2 interview Q5.)
- **Further documentation or diagram guards** — `docs/index.test.mjs` already holds the documentation page against the implemented product with 12 assertions. More documentation-shaped tests would add ceremony, not signal. Re-evaluate if documentation drift causes a real defect. (Source: Phase 2 interview Q5.)
- **Anything protecting a path with no production caller** — risk #7's sanitisation belongs to the change that introduces the first LLM caller, not to this plan. (Source: challenger pass, Phase 3.)

## 8. Freshness Ledger

- Strategy (§1–§5) last reviewed: 2026-08-24
- Stack versions last verified: 2026-08-24
- AI-native tool references last verified: 2026-08-24

Refresh (`/10x-test-plan --refresh`) when:

- a new top-3 risk surfaces from the roadmap or archive,
- a recommended tool's `checked:` date is older than three months,
- the project's tech stack changes (new framework, new test runner),
- §7 negative-space no longer matches what the team believes.

Known trigger already dated: risk #7 rises from Low to High when S-04b
introduces the first production LLM caller (roadmap window 09-03).
