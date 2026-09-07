---
change_id: pl-en-localization
title: Polish + English localization
status: impl_reviewed
created: 2026-09-04
updated: 2026-09-04
archived_at: null
---

## Notes

<!-- Free-form notes for this change: links, ad-hoc context, decisions that don't belong in research/frame/plan. -->

- Upstream: `context/foundation/prd-v2.md` (FR-001…FR-012) and `context/foundation/shape-notes.md`.
  Those settle the problem framing; this folder owns solution design only.
- Linear: **DEV-49** (In Progress, due 2026-09-11, labels `backend` + `frontend` + `Feature`).
  Branch: `thedariusz/dev-49-lokalizacja-pl-en-mechanizm-jezyka-na-koncie-angielski-jako`.
- Hard constraint: roadmap code freeze **2026-09-11**, submission 09-12/13. Increment 1 merges and
  is verified on production before the freeze.
- **Decided (2026-09-04), the eight solution-design calls:** see the Key Decisions table in
  `plan-brief.md`. The two that carry the most consequence are `Accept-Language` as the transport
  (with the account column as persistence), and `language` riding on `UserPrincipal` so
  `GET /api/users/me` keeps making zero database queries.
- **Accepted exception (2026-09-04):** FR-009 stays in increment 2, so a scheduled proposal is
  phrased in Polish regardless of the account language. This is wider than the PRD's Open Question 1
  framed it — the text is generated once at send time and stored, so the Polish reaches both the
  e-mail *and* the pending card in the SPA. Named at one seam (`ProposalService.proposeScheduled`)
  so pulling FR-009 forward stays a small change.
- **PRD open questions closed (2026-09-04), all three now recorded in `prd-v2.md`:**
  (1) *the inter-increment gap* — **accept the gap**, with the widened consequence written down: the
  scheduled text is stored, so the Polish reaches the pending card as well as the e-mail, and
  `ScheduledProposalTest` pins it so nobody removes it later believing it was a bug;
  (2) *English names for the 11 life domains* — **confirmed** and seeded by `V11`, product labels
  rather than translations (Health · Finances · Career & professional growth · Education & personal
  growth · Relationships · Home & surroundings · Leisure & hobbies · Admin & paperwork · Safety &
  preparedness · Transport & mobility · Inner growth & values), `display_order` untouched;
  (3) *English proposal quality* — **stays open** until the by-hand production walk on `2doai.app`.
  Nothing merged is evidence about it, and the `Verified against` note in `docs/index.html` says so
  rather than claiming a walk that has not happened.
