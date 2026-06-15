# AI + Memory Integration (F-02) — Plan Brief

> Full plan: `context/changes/ai-memory-integration/plan.md`
> Decision record (authoritative): `context/foundation/ai-provider.md`

## What & Why

Land the second roadmap foundation: a **swappable LLM client** (OpenRouter, behind a
port) and a **skeletal AI-memory mechanism** (semantic profile + bounded episodic log)
persisted to Postgres, with a render-to-context seam and the PRD "no-training" privacy
guardrail enforced **in code**. This unblocks the whole north-star path — AI memory
seed (S-03), proactive proposals (S-04/S-05), and AI auto-tag (S-09) — none of which
can exist until the app can talk to an LLM and remember the user.

## Starting Point

Spring Boot 4.0.6 / Java 25 backend with the F-01 persistence baseline done (Postgres
+ JPA + Flyway at `V2`, `category` reference table). `RestClient` is already on the
classpath (no new HTTP dep). UUIDv7 + audit-column conventions are documented but
**unused** — this change is their first real application. No LLM integration, no memory
tables, no `@ConfigurationProperties` class yet.

## Desired End State

The backend can call OpenRouter behind an `LlmClient` port (free-text + `json_schema
strict` structured output), persist a per-user AI-memory aggregate (profile facts +
episodic events), and render that memory to a deterministic markdown block ready for
prompt injection. The no-training guardrail is enforced per-request in code and
confirmed live. All hermetic tests pass in CI; a key-gated live test proves the real
Sonnet round-trip. No user-visible feature — only seams the next slices fill.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Provider / API shape | OpenRouter, OpenAI-compatible Chat Completions via Spring `RestClient` behind an `LlmClient` port | Budget control + swappable gateway; not the Anthropic SDK | Decision record |
| Model split | Haiku (auto-tag, later) / Sonnet (proposals, later), slug per call | Latency vs Polish quality; same client | Decision record |
| Memory mechanism | Structured profile + episodic log in Postgres, no vector DB | Clean DDD model + determinism + inspectability; RAG seam preserved | Decision record |
| Memory ownership | `user_id` UUID column now, **FK to `user` deferred** to S-01 (expand-only) | Keeps F-02 independent of S-01 (parallel) while the seam is real | Plan |
| Profile shape | `AiMemory` root + typed `ProfileFact` child rows (kind + content + provenance) | Flexible for any fact S-03 seeds; clean DDD; deterministic to render | Plan |
| Episodic log | Generic event rows; bound at **render-time** (last N), never deleted | Keeps full history as the post-MVP RAG seam; controls token cost | Plan |
| Inspectability/export | Render-to-markdown **seam only**; user-facing export deferred | One seam serves injection + future export; F-02 stays internal | Plan |
| Structured output | `json_schema strict`, verified on **Sonnet**; Haiku strict + A/B → S-09 | Honors the decision record; doesn't pull S-09 risk into the foundation | Plan |
| Live verification | `MockRestServiceServer` units (CI) + key-gated live test (skipped when no key) | CI stays hermetic; real contract still provable on demand | Plan |
| Privacy enforcement | Code-level per-request provider routing **+** runbook/dashboard doc | Guardrail in code, not just a flippable account toggle | Plan |
| Resilience | Timeouts + **one** transient retry (429/5xx), `maxAttempts` configurable | Recovers blips; configurable so auto-tag can dial it down for <500ms | Plan |

## Scope

**In scope:** `LlmClient` port + OpenRouter adapter; typed `LlmProperties`; free-text +
structured methods; in-code no-training routing; timeouts + configurable retry + typed
`LlmException`; Flyway `V3` (ai_memory root + profile_fact + episode); first UUIDv7 +
audit entities; `AiMemoryRepository`; render-to-markdown seam; hermetic + key-gated
tests; secret + dashboard + runbook + deploy; `data-model.md`/`CLAUDE.md` updates.

**Out of scope:** onboarding seed + enrichment writer (S-03); proposals/first-step
(S-04/S-05); auto-tag + Haiku verification + Polish A/B (S-09); `user` table/auth (S-01);
vector DB/RAG; user-facing export endpoint; Anthropic Java SDK; any prompt engineering.

## Architecture / Approach

Two clean boundaries onto the existing skeleton: a **port/adapter** for the LLM gateway
(swappable provider, no HTTP leaks to callers) and an **aggregate + repository** for
memory (a real domain object, not a data bag). A pure **renderer** bridges them:
`AiMemory → markdown block → (future) LlmRequest`. De-risk-first ordering — prove the
external integration hermetically before the conventional JPA work.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. LLM client (port + adapter) | Tested `LlmClient` over OpenRouter: free-text + structured, no-training routing, timeouts + retry | SB4/Java25 `RestClient` + OpenRouter request/response + `json_schema strict` contract details |
| 2. Memory aggregate | `V3` migration + first UUIDv7/audit entities + repository, ddl-validated | First `@UuidGenerator` + `jsonb` mapping under Hibernate 7 / SB4 |
| 3. Render seam | Deterministic markdown render of memory, last-N bounded | Getting the render contract right for downstream S-04 injection |
| 4. Provision + verify + deploy | Fly secret + dashboard no-training + live Sonnet test + deploy | Human-in-the-loop ops; live surprises (strict support, provider routing honored) |

**Prerequisites:** F-01 done (✓); Docker for tests; an OpenRouter account + credit (Phase 4);
`flyctl` access; deploy/infra already present.
**Estimated effort:** ~2–3 after-hours sessions across four phases (Phase 4 short but gated on manual ops).

## Open Risks & Assumptions

- **Haiku `json_schema strict` support** is unverified — explicitly deferred to S-09
  (F-02 proves the path on Sonnet only).
- **OpenRouter `provider` routing** must actually honor no-training/no-data-collection —
  confirmed live in Phase 4; the dashboard switches are the backstop.
- **First UUIDv7 + `jsonb`** usage under SB4/Hibernate 7 — verify the generator + JSON
  mapping wiring early in Phase 2.
- **`user_id` has no FK** until S-01 — accepted, documented inline in `V3`.
- **Credit-fee rate (item a)** unverified — captured in the Phase 4 runbook step.

## Success Criteria (Summary)

- `mvn test` green and hermetic (no key needed; live test auto-skips).
- With a real key, the live test round-trips Sonnet for both free-text and structured output.
- A persisted `AiMemory` reloads with children + audit + UUIDv7 ids and renders to a
  coherent, deterministic memory block; no-training is confirmed live and the secret ships to Fly.
