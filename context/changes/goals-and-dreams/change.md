---
change_id: goals-and-dreams
title: Goals and dreams
status: implementing
created: 2026-08-17
updated: 2026-08-17
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- Linear: DEV-19 (S-02, PRD refs FR-004/FR-005/FR-007; prerequisites S-01, F-01 — both done).
- **Decided (2026-08-17): one aggregate** for goal + dream — `layer` discriminator + nullable `horizon` — not two. Rationale: S-04/S-05/S-08/S-09 all consume the union; nothing but the horizon field differs.
