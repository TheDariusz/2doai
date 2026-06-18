# Lessons Learned

> Append-only register of recurring rules and patterns. Re-read at start by /10x-frame, /10x-research, /10x-plan, /10x-plan-review, /10x-implement, /10x-impl-review.

## Sanitize stored content before injecting it into an LLM prompt

- **Context**: AiMemoryRenderer (AiMemoryRenderer.java:80), and any future renderer, concatenates stored aggregate content — Episode.payload, profile facts — into a block fed to an LLM as system/context content (S-04 onward).
- **Problem**: Stored values originate (directly or indirectly) from user-influenced data. Concatenated verbatim into a prompt, a crafted payload can carry instructions into the model's context — a stored / second-order prompt-injection vector. The render seam is the natural choke point.
- **Rule**: Before any stored, user-influenced content reaches an LLM prompt, neutralize it at the render boundary — fence/delimit untrusted segments (wrap payloads in a clearly-marked data block the model is told to treat as data, not instructions) and/or strip control directives. Never concatenate raw stored content straight into prompt text.
- **Applies to**: Any code rendering persisted / user-influenced data into an LLM request (memory rendering, RAG context assembly, tool outputs). First live in S-04 (proposals) and S-09 (auto-tag).
