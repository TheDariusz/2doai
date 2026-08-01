<!-- IMPL-REVIEW-REPORT -->
# Implementation Review: AI + Memory Integration (F-02)

- **Plan**: context/changes/ai-memory-integration/plan.md
- **Scope**: Phase 3 of 4 (Context-Rendering Seam)
- **Date**: 2026-06-18
- **Verdict**: APPROVED (2 minor warnings, both fixed during triage)
- **Findings**: 0 critical, 2 warnings, 1 observation

## Verdicts

| Dimension | Verdict |
|-----------|---------|
| Plan Adherence | PASS |
| Scope Discipline | PASS |
| Safety & Quality | WARNING |
| Architecture | PASS |
| Pattern Consistency | WARNING |
| Success Criteria | PASS |

Success criteria verified: full suite green (26 tests after fixes, was 25), `AiMemoryRendererTest` 9/9 (3.1), full suite (3.2), rendered sample confirmed by user (3.3).

## Findings

### F1 — Unguarded occurredAt in episode comparator (latent NPE)

- **Severity**: ⚠️ WARNING
- **Impact**: 🔎 MEDIUM — real tradeoff; pause to reason through it
- **Dimension**: Safety & Quality
- **Location**: AiMemoryRenderer.java:39 (CHRONOLOGICAL) + :36 (BY_KIND_THEN_CONTENT)
- **Detail**: CHRONOLOGICAL sorts by `occurredAt` with no null guard while the id tiebreaker uses `nullsFirst` — an asymmetry. `recordEpisode(...)` never null-checked `occurredAt` (DB `nullable=false` is enforced only at flush), so an in-memory aggregate could NPE the sort. Same root cause covers null `kind`/`content` (BY_KIND_THEN_CONTENT NPEs first). Latent — no current caller can trigger it.
- **Fix A ⭐ Recommended**: `Objects.requireNonNull` in the `Episode` ctor (eventType, payload, occurredAt) and `ProfileFact` ctor (kind, content) — closes the NPE class at the domain boundary; the aggregate owns its invariants.
  - Strength: right altitude; makes "required" explicit instead of leaning on the DB.
  - Tradeoff: touches two Phase-2 files; re-run AiMemoryRepositoryTest.
  - Confidence: HIGH — existing callers all pass non-null values.
- **Fix B**: renderer-local `Comparator.nullsLast(...)` on occurredAt — partial (leaves kind/content unguarded).
- **Decision**: FIXED via Fix A — `requireNonNull` added to `Episode` and `ProfileFact` constructors; full suite green.

### F2 — No binding test for ai.memory.render.max-episodes

- **Severity**: ⚠️ WARNING
- **Impact**: 🏃 LOW — quick decision; fix is obvious and narrowly scoped
- **Dimension**: Pattern Consistency
- **Location**: MemoryProperties.java / application.properties:51 (no test)
- **Detail**: Sibling `LlmProperties` has `LlmPropertiesTest` proving its key binds. `MemoryProperties` had none — `AiMemoryRendererTest` constructs it with literals and never exercises the relaxed-binding path, so a key typo (`max-episodes` vs `maxEpisodes`) would ship silently.
- **Fix**: Add `MemoryPropertiesTest` mirroring `LlmPropertiesTest` — bind the property, assert `render().maxEpisodes() == 20`.
- **Decision**: FIXED — `MemoryPropertiesTest` added (`@SpringJUnitConfig` + `@TestPropertySource(classpath:application.properties)`); passes.

### F3 — Episode payload is a stored prompt-injection vector (S-04)

- **Severity**: 📝 OBSERVATION
- **Impact**: 🏃 LOW — no action this phase; track for S-04
- **Dimension**: Safety & Quality
- **Location**: AiMemoryRenderer.java:80
- **Detail**: The opaque episode payload is concatenated verbatim into a block destined for a Sonnet prompt. When S-04 wires this live, a crafted payload could carry instructions into the model context. The renderer is the right place to neutralize it (fence/escape), but the threat only materializes in S-04, which the plan defers.
- **Decision**: ACCEPTED-AS-RULE — recorded in `context/foundation/lessons.md` ("Sanitize stored content before injecting it into an LLM prompt"); no Phase-3 code change (deferred to S-04 by user choice).

## Note on change.md status

Status kept at `implementing` (not flipped to `impl_reviewed`): this is a phase-scoped review mid-implementation with Phase 4 still pending. `impl_reviewed` would prevent `/10x-implement` from resetting to `implementing` when Phase 4 starts. Revisit at full-plan review.
