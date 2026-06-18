# AI + Memory Integration (F-02) Implementation Plan

## Overview

Land the second roadmap foundation: a **swappable LLM client** and a **skeletal
AI-memory mechanism**, wired onto the F-01 persistence baseline. After this change
the backend can talk to OpenRouter (behind a port), persist a DDD AI-memory
aggregate (semantic profile + bounded episodic log) per user, render that memory to
a context block for prompt injection, and it honors the PRD hard guardrail (data and
memory never train external models) **in code**, not just by an account toggle.

No user-visible effect. This foundation only builds *seams*; the user-facing flows
that fill them arrive later: onboarding seed + enrichment-on-completion (S-03),
proactive proposals + first step (S-04/S-05), and AI category auto-tag (S-09).

The big architectural decisions are already settled in
`context/foundation/ai-provider.md` (provider = OpenRouter→first-party Anthropic;
model split Haiku/Sonnet; no-training privacy; memory = structured profile +
episodic log, no vector DB). This plan is execution + the solution-design choices
confirmed during planning.

> **⟳ Revised 2026-06-15 — LLM transport is now Spring AI 2.0, not a hand-rolled
> `RestClient`.** Spring AI 2.0.0 GA (2026-06-12) requires Spring Boot 4.0, so it fits
> this backend. The `LlmClient` port and the four provider-neutral types are unchanged;
> only the adapter changed: `OpenRouterLlmClient` → **`SpringAiLlmClient`**, delegating to
> Spring AI's auto-configured `ChatModel` (OpenAI client pointed at OpenRouter via
> `spring.ai.openai.base-url`). Consequences that supersede the Phase-2 detail below:
> transport/timeout/**retry (429/5xx, fail-fast on other 4xx) are owned by the OpenAI
> client** (`spring.ai.openai.timeout` / `spring.ai.openai.max-retries`), so the adapter
> writes no retry code; the no-training `provider` block rides on every request via
> `OpenAiChatOptions.extraBody(...)` → OpenAI `additionalBodyProperties`; structured output
> uses `OpenAiChatModel.ResponseFormat(JSON_SCHEMA)` (Spring AI forces `strict:true`); the
> adapter is unit-tested against a mocked `ChatModel` (`SpringAiLlmClientTest`) since there
> is no Spring `RestClient` to intercept. `LlmProperties` shrank to just the model slugs.

## Current State Analysis

The backend is Spring Boot **4.0.6** / Java **25** with the F-01 persistence baseline
in place:

- **Domain-package convention**: `com.thedariusz.todoai.<subdomain>` — `category/`
  is the template (plain JPA, no Lombok, getters-only entities, `JpaRepository`).
- **`RestClient` is already on the classpath** — `spring-boot-starter-webmvc` pulls
  in `spring-web`. No new HTTP dependency is needed. `spring-boot-starter-validation`
  is **absent**.
- **Flyway** is at `V2` (`V1__create_category.sql`, `V2__seed_categories.sql`); next
  migration is **`V3`**. Migrations are expand-only.
- **UUIDv7 convention is documented but unused** — `Category` uses a natural key. The
  AI-memory aggregate will be the **first real UUIDv7 entity** (Hibernate
  `@UuidGenerator`, RFC 9562) and the first to carry `created_at`/`updated_at`
  `timestamptz` audit columns.
- **`ddl-auto=validate`** — Hibernate validates mappings against the Flyway schema; a
  free drift guard for the new entities.
- **Config** is flat `application.properties` with env-var secrets in prod; **no
  `@ConfigurationProperties` class exists yet** (this change introduces the first).
- **Tests**: Testcontainers Postgres 18 (`@ServiceConnection`) + `@SpringBootTest`;
  `@WebMvcTest` for web slices. `MockRestServiceServer` is **not yet used** (this
  change introduces it for the adapter).
- **Secrets/deploy**: prod datasource via Fly secrets (Neon); `fly.toml` always-on;
  Cloudflare Pattern B. `OPENROUTER_API_KEY` will follow the same Fly-secret pattern.

### Key Discoveries:

- `backend/src/main/java/com/thedariusz/todoai/category/Category.java` — entity style
  to mirror (explicit `@Entity`/`@Table`, `@Column(nullable=false)`, protected no-arg
  ctor, getters only).
- `backend/src/main/java/com/thedariusz/todoai/category/CategorySyncCheck.java` —
  `ApplicationRunner` startup-invariant pattern (available if a memory-side invariant
  is ever needed; none required in F-02).
- `backend/src/main/resources/application.properties` — flat config; `ddl-auto=validate`,
  `open-in-view=false`, actuator liveness/readiness split already tuned.
- `backend/src/test/java/com/thedariusz/todoai/TestcontainersConfiguration.java` —
  `@ServiceConnection` Postgres 18 bean imported by `@SpringBootTest` integration tests.
- `backend/pom.xml` — SB 4.0.6, Java 25; `spring-web` present (RestClient), no
  validation starter, no HTTP-mock test dep beyond what `spring-boot-starter-webmvc-test`
  ships (it includes `spring-test`, which provides `MockRestServiceServer`).
- `context/foundation/ai-provider.md` — the authoritative decision record; its
  "Integration implications" and "Do zweryfikowania przy implementacji" (a/b/c/d)
  sections drive Phases 1 and 4.

## Desired End State

A backend that:

1. Exposes an `LlmClient` port with a `complete(...)` (free text) and a
   `completeStructured(...)` (JSON via `response_format: json_schema, strict`) method,
   implemented by an OpenRouter `RestClient` adapter that is model-agnostic at the
   call site (model slug passed per call), enforces no-training provider routing in
   the request body, applies configurable timeouts + one transient retry, and surfaces
   failures as a typed `LlmException`.
2. Persists a per-user **AI-memory aggregate** — an `ai_memory` root (keyed by a
   `user_id` UUID, FK deferred to S-01) holding **profile facts** (typed `kind` +
   `content` + provenance) and an **episodic log** (generic `event_type` + `payload` +
   `occurred_at`), with UUIDv7 PKs and audit columns, validated by Hibernate against a
   Flyway `V3` migration.
3. Renders an `AiMemory` to a deterministic markdown/text block (all profile facts +
   the last *N* episodes) — the seam used both for prompt injection (S-04) and future
   user-facing export.
4. Is verified: hermetic `MockRestServiceServer` unit tests run in CI; a live
   integration test (gated on `OPENROUTER_API_KEY`) round-trips real Sonnet for both
   free-text and structured output; the no-training switches are confirmed live and
   the secret ships to Fly.

**Verification**: `mvn test` green (hermetic); the key-gated live test passes when run
with a real key; `data-model.md` shows the three new tables; the deployed app boots
with the LLM config present and the Fly `OPENROUTER_API_KEY` secret set.

## What We're NOT Doing

- **No user-facing flows**: no onboarding questions (S-03), no enrichment-on-completion
  writer (S-03), no proposal generation or first-step (S-04/S-05), no auto-tag (S-09).
  The episodic log table exists but **nothing writes to it** in F-02.
- **No auth / `user` table** (S-01) — `user_id` is an unconstrained UUID column; the FK
  is added by a later expand-only migration when S-01 lands.
- **No Haiku structured-output verification** — the `json_schema strict` path is proven
  against **Sonnet** only; Haiku's strict support + the Polish A/B + any fallback
  (tool-calling / prompted JSON / route-to-Sonnet) are deferred to **S-09**, where
  auto-tag actually lands (`ai-provider.md` items b and c).
- **No vector DB / embeddings / RAG** — explicitly out of MVP per the decision record;
  the persistent episodic rows are the future RAG seam.
- **No user-facing export endpoint** — only the render-to-markdown domain seam; exposing
  it needs auth + UI (later slice).
- **No Anthropic Java SDK** — OpenRouter is OpenAI-compatible; we reach it through Spring AI 2.0's OpenAI client (see the revision note above; originally a hand-rolled `RestClient`).
- **No prompt engineering for proposals/auto-tag** — prompts belong to the slices that
  own those features.

## Implementation Approach

De-risk-first ordering, four phases mirroring the F-01 shape (plumbing → data →
seam → provision/verify/deploy):

1. **LLM client first** — it carries the genuine unknowns (external contract, SB4/Java25
   `RestClient`, OpenRouter request/response shape, structured output, provider routing).
   Proving it hermetically (mock) early means the rest is conventional Spring/JPA work.
2. **Memory aggregate** — conventional JPA, but the *first* UUIDv7 + audit-column entity,
   so it also exercises the documented convention end-to-end.
3. **Render seam** — small, pure, deterministic; ties memory to the eventual prompt call.
4. **Provision + live-verify + deploy** — the human-in-the-loop ops step that turns the
   hermetic build into a live-verified one (mirrors F-01 Phase 3 / Neon).

Both new layers sit behind clean boundaries (port/adapter for the LLM, an aggregate +
repository for memory) per the CLAUDE.md clean-architecture / DDD preference, so the
gateway is swappable and the memory model is a real domain object, not a data bag.

## Critical Implementation Details

- **OpenRouter request shape**: `POST {base-url}/chat/completions`, `Authorization:
  Bearer {key}`, OpenAI-compatible body. Structured output uses
  `response_format: { type: "json_schema", json_schema: { strict: true, schema: {...} } }`.
  No-training routing goes in the **same body** via the `provider` field (e.g.
  `provider: { data_collection: "deny" }` and/or training-off routing) — this is the
  in-code half of the privacy guardrail and is one of the live-verify items (Phase 4).
  Model slugs use **dots** (`anthropic/claude-sonnet-4.6`), not first-party dashes.
- **Retry vs latency**: the single transient retry (429/5xx + backoff) is the foundation
  default, but the attempt count must be a **config value** on `LlmProperties` so S-09's
  latency-bound auto-tag (<500ms feedback budget) can lower it without touching the
  adapter. Do not retry on 4xx other than 429.
- **Aggregate identity & FK timing**: the `ai_memory` root's `user_id` is a plain UUID
  column **now** (unique, indexed), with **no FK** — the FK to `user(id)` is an
  expand-only migration in S-01. Document this deferral inline in the `V3` migration so
  it isn't mistaken for an oversight.
- **First UUIDv7 entity**: this is the first use of `@UuidGenerator` (RFC 9562 v7) and
  the first `created_at`/`updated_at` `timestamptz` audit columns. Confirm the exact
  Hibernate 7 / SB4 generator wiring resolves before building all three entities.
- **Episodic bounding lives in render code, not the schema** — never delete rows; the
  "last N" cap is applied when rendering the context block.

## Phase 1: LLM Client (port + adapter)

### Overview

A swappable `LlmClient` port and an OpenRouter `RestClient` adapter with typed config,
free-text + structured methods, in-code no-training routing, timeouts + one retry, and
a typed exception — proven hermetically with `MockRestServiceServer` plus a key-gated
live test stub.

### Changes Required:

#### 1. LLM port + domain types

**File**: `backend/src/main/java/com/thedariusz/todoai/ai/LlmClient.java` (+ small
supporting types in the same `ai` package)

**Intent**: Define the gateway the rest of the app depends on, so the provider is
swappable and call sites never see HTTP. Two operations: free-text completion and
schema-constrained structured completion. Each takes the model slug per call (model
split is a caller concern), plus the messages/prompt.

**Contract**: `LlmClient` interface with two methods — roughly
`String complete(LlmRequest request)` and
`<T> T completeStructured(LlmRequest request, Class<T> type, JsonSchema schema)` (exact
shape at implementer's discretion). `LlmRequest` carries model slug + messages +
optional params. A typed `LlmException extends RuntimeException` is the single failure
type callers handle. No Spring/HTTP types leak across this boundary.

#### 2. Typed configuration

**File**: `backend/src/main/java/com/thedariusz/todoai/ai/LlmProperties.java` +
additions to `application.properties`

**Intent**: First `@ConfigurationProperties` in the project — holds base URL, the two
model slugs, timeouts (connect/read), retry attempt count, and the API key reference.
Keeps the adapter free of magic strings and lets ops tune behavior without code.

**Contract**: `@ConfigurationProperties(prefix = "llm")` record/class with fields:
`baseUrl`, `model.haiku`, `model.sonnet` (or a map), `timeout.connect`, `timeout.read`,
`retry.maxAttempts`, `apiKey`. `application.properties` binds them, with `apiKey` from
`${OPENROUTER_API_KEY}` (env, never committed). Register via
`@EnableConfigurationProperties` (or `@ConfigurationPropertiesScan`).

#### 3. OpenRouter adapter

**File**: `backend/src/main/java/com/thedariusz/todoai/ai/OpenRouterLlmClient.java` +
a `RestClient` bean (config class in the same package)

**Intent**: Implement `LlmClient` against OpenRouter's OpenAI-compatible
`/chat/completions`. Build the request body (model, messages, optional
`response_format` for structured calls, and the `provider` no-training routing block),
send via `RestClient`, map the response back to text or the typed object, translate
transport/HTTP errors into `LlmException`, and apply timeouts + one transient retry.

**Contract**: `@Component` implementing `LlmClient`; constructor-injected `RestClient`
(built from `LlmProperties` timeouts + bearer auth) and `LlmProperties`. Structured
calls set `response_format: {type:"json_schema", json_schema:{strict:true, schema}}`.
Every request body includes the `provider` no-training routing field. Retry: configured
`maxAttempts` on 429/5xx with short backoff; no retry on other 4xx. The Jackson
(de)serialization of the structured payload is the only non-obvious bit — keep the
response→DTO mapping centralized.

### Success Criteria:

#### Automated Verification:

- Project compiles: `mvn -q -DskipTests compile`
- Adapter unit tests pass against a mocked transport: `mvn test -Dtest=OpenRouterLlmClientTest`
  — covers (a) free-text happy path, (b) structured `json_schema strict` request shape +
  typed deserialization, (c) the `provider` no-training routing block is present on every
  request, (d) 429/5xx triggers exactly `maxAttempts` then `LlmException`, (e) a 4xx (≠429)
  fails fast as `LlmException` with no retry.
- Config binds: a context test asserts `LlmProperties` populates from `application.properties`.
- Full suite green: `mvn test`
- No secret in repo: `git grep -nE 'sk-or-|OPENROUTER_API_KEY *= *[A-Za-z0-9]'` returns nothing.

#### Manual Verification:

- The `provider` routing JSON matches OpenRouter's current API for disabling
  training/data-collection (cross-check docs; final live confirmation is Phase 4).
- Timeout values are sane for a generation call (read timeout comfortably above expected
  latency) yet bounded.

**Implementation Note**: After Phase 1 automated verification passes, pause for manual
confirmation before Phase 2. Phase blocks use plain bullets; checkboxes live in `## Progress`.

---

## Phase 2: Memory Aggregate (schema + domain + persistence)

### Overview

The DDD AI-memory aggregate persisted to Postgres: an `ai_memory` root per user with
profile-fact and episode children, the first UUIDv7 + audit-column entities, plus a
repository. Schema by Flyway `V3`, validated by Hibernate. No writers.

### Changes Required:

#### 1. Flyway migration

**File**: `backend/src/main/resources/db/migration/V3__create_ai_memory.sql`

**Intent**: Create the three tables for the aggregate. Root keyed by `user_id` (UUID,
unique, **no FK yet** — documented deferral to S-01). Children reference the root id.
All carry UUIDv7 PKs and `created_at`/`updated_at` `timestamptz` audit columns;
episodes carry `occurred_at` too.

**Contract**: Tables —
`ai_memory(id uuid pk, user_id uuid not null unique, created_at, updated_at)`;
`ai_memory_profile_fact(id uuid pk, ai_memory_id uuid not null → ai_memory.id, kind
varchar not null, content text not null, provenance varchar, created_at, updated_at)`;
`ai_memory_episode(id uuid pk, ai_memory_id uuid not null → ai_memory.id, event_type
varchar not null, payload jsonb not null, occurred_at timestamptz not null, created_at)`.
Index `ai_memory(user_id)` and the two child FKs. Expand-only. An inline SQL comment
notes the `user_id` FK is intentionally deferred to S-01.

#### 2. Aggregate entities

**File**: `backend/src/main/java/com/thedariusz/todoai/ai/memory/AiMemory.java`,
`ProfileFact.java`, `Episode.java`

**Intent**: Model the aggregate as a real domain object: `AiMemory` is the root holding
collections of `ProfileFact` and `Episode`, with behavior to add a fact / record an
episode (used by S-03/S-04 later) rather than exposing raw setters. First use of the
documented UUIDv7 + audit convention.

**Contract**: `AiMemory` `@Entity` with `@UuidGenerator` id, `userId`, `@OneToMany`
(cascade, orphan-removal) to `ProfileFact` and `Episode`, audit columns, and intent-
revealing methods (e.g. `addFact(kind, content, provenance)`, `record(episode)`).
Children are `@Entity` with UUIDv7 ids, mapped to the snake_case columns. Plain JPA, no
Lombok, mirroring `Category`'s style. `Episode.payload` maps to `jsonb` (Hibernate JSON
mapping). The UUIDv7 generator + `jsonb` mapping are the only non-obvious wiring.

#### 3. Repository

**File**: `backend/src/main/java/com/thedariusz/todoai/ai/memory/AiMemoryRepository.java`

**Intent**: Load/save the aggregate by user. Read the whole aggregate (root + children)
for rendering; create-on-first-use is a later (S-03) concern but the finder exists now.

**Contract**: `interface AiMemoryRepository extends JpaRepository<AiMemory, UUID>` with
`Optional<AiMemory> findByUserId(UUID userId)`. Fetch strategy for children chosen to
avoid N+1 when rendering (e.g. entity graph / join fetch) — implementer's call.

#### 4. Docs

**File**: `context/foundation/data-model.md`, `CLAUDE.md`

**Intent**: Extend the canonical ERD with the three tables and move the AI-memory line
from "Planned" to drawn; note the deferred `user_id` FK. Add a one-line persistence note
if the convention gained anything (first UUIDv7 + jsonb usage).

**Contract**: ERD updated; "Planned (not yet designed)" list updated; no behavioral code.

### Success Criteria:

#### Automated Verification:

- Migration applies + Hibernate validates on a real DB: `mvn test -Dtest=ApplicationTests`
  (boots full context on Testcontainers; `ddl-auto=validate` passes against `V3`).
- Aggregate round-trips: `mvn test -Dtest=AiMemoryRepositoryTest` — persist an `AiMemory`
  with ≥1 `ProfileFact` and ≥1 `Episode` (incl. `jsonb` payload), reload by `user_id`,
  assert children + audit columns + UUIDv7 ids populate.
- Full suite green: `mvn test`

#### Manual Verification:

- `data-model.md` ERD renders and shows the three tables + deferred-FK note.
- UUIDv7 ids are time-ordered (spot-check two inserts) — confirms the v7 strategy, not v4.

**Implementation Note**: Pause for manual confirmation after automated verification before Phase 3.

---

## Phase 3: Context-Rendering Seam

### Overview

A pure domain service that renders an `AiMemory` to a deterministic markdown/text block —
the mechanism by which memory is injected into a future Sonnet prompt, and the source a
future export endpoint will reuse. Render-time episodic bounding lives here.

### Changes Required:

#### 1. Renderer

**File**: `backend/src/main/java/com/thedariusz/todoai/ai/memory/AiMemoryRenderer.java`
(domain service)

**Intent**: Turn an `AiMemory` into a stable, human-readable markdown block: all profile
facts (grouped/ordered deterministically) + the **last N** episodes by `occurred_at`
(N from config). Deterministic output (stable ordering) so prompts are reproducible and
the block is diff-friendly for future export.

**Contract**: `String render(AiMemory memory)` (and/or `render(AiMemory, int maxEpisodes)`).
Episode cap sourced from config (reuse `LlmProperties` or a small memory config value).
Empty/absent sections render predictably (no nulls, no dangling headers). No persistence,
no HTTP — pure function over the aggregate.

#### 2. Wiring note (no behavior yet)

**Intent**: Confirm the renderer's output is shaped to drop directly into an `LlmRequest`
message as system/context content, so S-04 wires `render(...)` → `LlmRequest` with zero
reshaping. No call is made in F-02.

**Contract**: A unit-level assertion (or doc note) that the rendered block is a plain
string suitable as a message part; no proposal call exists yet.

### Success Criteria:

#### Automated Verification:

- Renderer is deterministic + bounded: `mvn test -Dtest=AiMemoryRendererTest` — same
  input → identical output; only the last N episodes appear when more exist; empty
  profile / empty episodes render without errors or stray headers.
- Full suite green: `mvn test`

#### Manual Verification:

- Eyeball a rendered sample for a seeded `AiMemory`: reads like a coherent "what the AI
  knows about you" block a human could review (serves the inspectability guardrail).

**Implementation Note**: Pause for manual confirmation after automated verification before Phase 4.

---

## Phase 4: Provision + Privacy + Live Verification + Deploy

### Overview

The human-in-the-loop ops step: provision the OpenRouter key, set the Fly secret,
configure dashboard no-training/no-logging, run the gated live test against real Sonnet,
resolve the `ai-provider.md` to-verify items, and deploy so the config ships. Turns the
hermetic build into a live-verified one. Mirrors F-01 Phase 3 (Neon).

### Changes Required:

#### 1. Key-gated live integration test

**File**: `backend/src/test/java/com/thedariusz/todoai/ai/OpenRouterLiveTest.java`

**Intent**: A real round-trip against OpenRouter, **skipped unless `OPENROUTER_API_KEY`
is present** so CI stays hermetic and green. Exercises both a free-text completion and a
`json_schema strict` structured completion on **Sonnet**, asserting a well-formed
response and a correctly-typed structured object.

**Contract**: `@EnabledIfEnvironmentVariable(named="OPENROUTER_API_KEY", matches=".+")`
on a `@SpringBootTest` (or a focused wiring of the real `RestClient`). Two tests: free
text, structured. Asserts no exception + parseable result. Low token usage (tiny prompt).

#### 2. Secret + dashboard config (ops, documented)

**File**: `context/foundation/deployment-runbook.md` (new section)

**Intent**: Record the exact ops: create the OpenRouter key (low per-key credit cap as
the budget backstop), `fly secrets set OPENROUTER_API_KEY=…`, local injection via env
(never a committed `.env`), and the dashboard switches (prompt-logging off, training
routing off). Capture the live-confirmation checklist.

**Contract**: Runbook section with the commands (no secret values), the dashboard
checklist, and a "no-training verified on YYYY-MM-DD" line to fill in. Resolves
`ai-provider.md` items **(a)** credit-fee rate, **(b on Sonnet)** `json_schema strict`
confirmed for Sonnet, **(d)** no-training/logging switches off live. Item **(c)** (Haiku
Polish A/B) and Haiku's strict support remain explicitly deferred to S-09.

#### 3. Deploy

**Intent**: Deploy the backend so the new config + secret ship. No user-visible change;
the app must boot with the LLM config present and the secret resolvable.

**Contract**: Standard Fly deploy via the existing GitHub Actions / `flyctl` path; app
boots, `/actuator/health` liveness stays `UP`.

### Success Criteria:

#### Automated Verification:

- Hermetic CI unaffected: `mvn test` green **without** a key (live test auto-skips).
- With a key set locally, the live test passes: `OPENROUTER_API_KEY=… mvn test -Dtest=OpenRouterLiveTest`
  — both free-text and structured-on-Sonnet round-trips succeed.

#### Manual Verification:

- `fly secrets list` shows `OPENROUTER_API_KEY` set; no key value anywhere in the repo.
- OpenRouter dashboard: prompt-logging off, training routing off — confirmed live;
  runbook line dated.
- Deployed app boots; liveness `UP`; a manual one-off live call (or the live test run
  against prod config) succeeds.
- `ai-provider.md` items (a), (b-on-Sonnet), (d) marked resolved; (c) + Haiku strict
  noted as deferred to S-09.

**Implementation Note**: This phase has human-in-the-loop ops (key creation, dashboard,
deploy). Pause for explicit confirmation that the live verification passed before
considering F-02 done.

---

## Testing Strategy

### Unit Tests:

- `OpenRouterLlmClientTest` (`MockRestServiceServer`): free-text, structured request
  shape + typed deserialization, no-training `provider` block present, retry on 429/5xx
  to `maxAttempts`, fail-fast on other 4xx, error→`LlmException` translation.
- `LlmProperties` binding test.
- `AiMemoryRendererTest`: determinism, last-N bounding, empty-section handling.

### Integration Tests:

- `AiMemoryRepositoryTest` (Testcontainers): aggregate persist/reload by `user_id`,
  children, `jsonb` payload, UUIDv7 + audit columns.
- `ApplicationTests` (existing): full context boot validates `V3` under `ddl-auto=validate`.
- `OpenRouterLiveTest` (gated on `OPENROUTER_API_KEY`): real Sonnet free-text + structured.

### Manual Testing Steps:

1. Run `mvn test` with no key → all green, live test skipped.
2. Set a real key locally → run the live test → both round-trips pass.
3. Inspect a rendered `AiMemory` block for coherence.
4. Confirm dashboard no-training/no-logging live; date the runbook.
5. Deploy; confirm liveness `UP` and the secret is set.

## Performance Considerations

- Auto-tag's <500ms feedback budget is an **S-09** concern, but F-02 must not preclude
  it: hence the configurable retry count (so auto-tag can set `maxAttempts=1`) and bounded
  read timeout. Proposal calls (Sonnet) are rare (~1 per 2–7 days), so latency there is a
  non-issue.
- Rendering injects only the last N episodes → bounded prompt-token cost regardless of
  episodic history size.
- Memory reads fetch the aggregate's children in one go (avoid N+1) since rendering always
  needs them.

## Migration Notes

- `V3` is expand-only (create-only); safe under an image rollback.
- The `ai_memory.user_id` FK to `user(id)` is **deliberately deferred** to S-01's
  migration (expand-only add-constraint) — documented inline in `V3`.
- No data backfill (no rows exist; writers arrive in S-03/S-04).

## References

- Decision record (authoritative): `context/foundation/ai-provider.md`
- Roadmap slice: `context/foundation/roadmap.md` → F-02
- Data model: `context/foundation/data-model.md`
- Prior foundation (patterns to mirror): `context/archive/2026-06-13-persistence-baseline/plan.md`
- Entity style: `backend/src/main/java/com/thedariusz/todoai/category/Category.java`
- Test wiring: `backend/src/test/java/com/thedariusz/todoai/TestcontainersConfiguration.java`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands.
> Do not rename step titles. See `references/progress-format.md`.

### Phase 1: LLM Client (port + adapter)

#### Automated

- [x] 1.1 Project compiles (`mvn -q -DskipTests compile`)
- [x] 1.2 Adapter unit tests pass — free-text, structured shape + deserialization, no-training routing present, retry to maxAttempts, fail-fast on other 4xx (`OpenRouterLlmClientTest`)
- [x] 1.3 `LlmProperties` binds from `application.properties`
- [x] 1.4 Full suite green (`mvn test`)
- [x] 1.5 No secret in repo (`git grep` check returns nothing)

#### Manual

- [x] 1.6 `provider` no-training JSON matches OpenRouter's current API (doc cross-check) — 67f9e46
- [x] 1.7 Timeout values sane for a generation call — 67f9e46

### Phase 2: Memory Aggregate (schema + domain + persistence)

#### Automated

- [x] 2.1 `V3` applies + Hibernate validates on Testcontainers (`ApplicationTests`) — cf8b9c5
- [x] 2.2 Aggregate persist/reload by user_id with children + jsonb + UUIDv7 + audit (`AiMemoryRepositoryTest`) — cf8b9c5
- [x] 2.3 Full suite green (`mvn test`) — cf8b9c5

#### Manual

- [x] 2.4 `data-model.md` ERD shows the three tables + deferred-FK note — cf8b9c5
- [x] 2.5 UUIDv7 ids are time-ordered (spot-check) — cf8b9c5

### Phase 3: Context-Rendering Seam

#### Automated

- [x] 3.1 Renderer deterministic + last-N bounded + empty-section safe (`AiMemoryRendererTest`) — cf1d8c9
- [x] 3.2 Full suite green (`mvn test`) — cf1d8c9

#### Manual

- [x] 3.3 Rendered sample reads as a coherent "what the AI knows about you" block — cf1d8c9

### Phase 4: Provision + Privacy + Live Verification + Deploy

#### Automated

- [x] 4.1 Hermetic CI green without a key (live test auto-skips) — 67f9e46
- [x] 4.2 Live test passes with a real key — Sonnet free-text + structured (`OpenRouterLiveTest`) — 67f9e46

#### Manual

- [x] 4.3 `fly secrets list` shows `OPENROUTER_API_KEY`; no key value in repo — 67f9e46
- [x] 4.4 Dashboard no-training/no-logging confirmed live; runbook line dated — 67f9e46
- [x] 4.5 Deployed app boots; liveness `UP`; live call against prod config succeeds
- [x] 4.6 `ai-provider.md` items (a), (b-on-Sonnet), (d) resolved; (c) + Haiku strict deferred to S-09 — 67f9e46
