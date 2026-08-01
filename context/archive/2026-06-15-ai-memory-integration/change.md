---
id: ai-memory-integration
title: "AI + memory integration — LlmClient (OpenRouter) + AI-memory aggregate + no-training guardrail"
roadmap_id: F-02
status: archived
archived_at: 2026-08-01T19:03:24Z
created: 2026-06-15
updated: 2026-08-01
prd_refs:
  - NFR (prywatność danych/pamięci; widoczny feedback AI <500ms)
  - PRD Open Question #2 (rozstrzygnięte — context/foundation/ai-provider.md)
  - FR-008, FR-009, FR-010, FR-011–014 (capability seams; wired by S-03/S-04/S-09)
---

# AI + memory integration (F-02)

Second foundation of the 2do AI roadmap: connect a swappable LLM client and a
skeletal AI-memory mechanism onto the persistence baseline (F-01). Establishes a
`LlmClient` port + `SpringAiLlmClient` adapter (OpenRouter's OpenAI-compatible Chat
Completions via **Spring AI 2.0**'s OpenAI client, not a hand-rolled `RestClient` or
the Anthropic SDK), a DDD AI-memory aggregate (semantic
profile facts + bounded episodic log) persisted to Postgres, a render-to-context
seam, and the PRD "no-training" privacy guardrail enforced in code. No
user-visible effect — onboarding/enrichment (S-03), proposals (S-04/S-05) and
auto-tag (S-09) wire the seams later.

Key decisions are pre-settled in `context/foundation/ai-provider.md` (provider,
model split, privacy, memory mechanism).

Unlocks: S-03 (ai-memory-seed), S-04 (proactive-proposal-engine),
S-05 (natural-rhythm-return), S-09 (ai-category-autotag).

- Plan: `plan.md`
- Brief: `plan-brief.md`
