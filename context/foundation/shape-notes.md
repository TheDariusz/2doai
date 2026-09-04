---
project: "2do AI"
context_type: brownfield
created: 2026-09-03
updated: 2026-09-04
checkpoint:
  current_phase: 8
  phases_completed: [1, 2, 3, 4, 5, 6, 7]
  gray_areas_resolved:
    - topic: "pain / gap"
      decision: "English readers can't use it (someone shown the app, submission/repo reviewers, future growth); product to become English-first"
    - topic: "insight — why not done already"
      decision: "nothing deep; cut for the 2026-09-14 deadline. Obvious work."
    - topic: "must preserve"
      decision: "the natural-rhythm loop keeps firing; Polish preserved as a language, not as the default; existing accounts keep Polish without acting"
    - topic: "change category"
      decision: "architectural improvement — the locale mechanism is the deliverable, English copy rides on it"
    - topic: "primary persona scope"
      decision: "new English-reading user landing on 2doai.app; existing Polish account switching is secondary"
    - topic: "auth model"
      decision: "no change; account gains one setting: preferred language"
    - topic: "pre-login language"
      decision: "browser language, English fallback, with a visible switch on the auth screens (FR-001 revised in the Socrates round)"
    - topic: "first slice vs 09-14 deadline"
      decision: "scoped down — increment 1 (UI, categories, on-demand proposal + fallback) by 2026-09-14; increment 2 (rhythm e-mail) after submission, so the protected loop is not touched before the freeze"
    - topic: "domain rule delta"
      decision: "no domain logic change — language is a presentation concern"
    - topic: "where the language lives"
      decision: "on the account, one defaulted column, existing rows = Polish; client-only rejected because e-mails and the Polish default need the server to know"
    - topic: "product type / user base"
      decision: "no change — web app (PWA) + API; opens to English readers, still a handful"
  frs_drafted: 12
  quality_check_status: accepted
product_type: web-app
target_scale:
  users: small
  qps: low
  data_volume: small
timeline_budget:
  delivery_weeks: 1
  hard_deadline: 2026-09-14
  after_hours_only: true
---

# 2do AI — Shape Notes: PL + EN localization

> Seed idea (verbatim): "I'd like to add to our service localization feature. I'd like to have Polish and English languange in the app"

> Prior greenfield session archived under `context/foundation/archive/` — it produced `prd.md`. This session shapes a change to that system.

## Current System

**2do AI** — a personal AI-powered todo and planning app, live at `2doai.app`. Three task layers
(task / goal / dream) in one `goal` aggregate across 11 fixed life domains; a proposal engine picks a
neglected entry, an LLM phrases the proposal, and the natural-rhythm scheduler (S-05, live since
2026-09-01) returns to the user by e-mail without being asked.

- **Architecture:** two deployables behind one origin — Spring Boot 4 / Java 25 REST API on Fly.io
  with Postgres 18 on Neon; React 19 + Vite PWA on Cloudflare Pages proxying `/api/*`.
  Server-side session cookie auth; Flyway-owned schema.
- **Users today:** the author plus "people like the author" — long-term planners. Production
  verified by hand on 2026-09-03. Polish is the only language of the UI, proposals, e-mails and data.
- **Locale seams already in place:** `category.name_pl` is served as a language-neutral `name`
  (an `Accept-Language` header was reserved for the day a second language lands); prompts *name* the
  output language (`ProposalPrompt.OUTPUT_LANGUAGE`) instead of being written in it; the SPA renders
  whatever language the server picks and never chooses a locale itself (`<html lang="pl">`).
- **Polish-bound surfaces:** hardcoded SPA copy, the `ProposalTemplate` fallback sentence,
  `ProposalEmail`, category names, and everything users have typed or the AI has memorised.

## Vision & Problem Statement

Anyone who does not read Polish cannot use 2do AI: a person shown the app, a reviewer of the
10xBuilder submission or the public repository, a future signup. The product is Polish-only in its
copy, its generated text and its e-mails, so the audience stops at one language.

The change: the app speaks **English and Polish**, and becomes **English-first**. Polish is
preserved as a language, not as the default. There is no deep reason this was not done already — it
was cut for the 2026-09-14 deadline and is otherwise obvious work. The delivered thing is the locale
*mechanism*; English copy is what rides on it.

## User & Persona

**Primary: a new English-reading user** landing on `2doai.app` — a reviewer, a friend, a future
signup — who must get through sign-up, the goals view, a proposal and its e-mail without meeting a
Polish word.

### Secondary persona

**The existing Polish account (the author)** who flips the language to English and back and expects
the app to follow in every surface, not only the labels.

## Access Control

No change to authentication or roles — email + password, flat single-tenant, no sharing. The account
gains **one setting: preferred language** (Polish or English), chosen by the user and applied to every
surface addressed to them once logged in.

Before login (sign-in, sign-up) the language follows the **browser language, with English as the
fallback**: a Polish browser sees Polish, everyone else sees English.

## Success Criteria

### Primary

A new English-reading visitor on `2doai.app` signs up, sees the shell, the goals view, the forms and
the 11 category names in English, adds entries, requests a proposal and receives the proposal and its
first step phrased in English — without meeting a Polish word. The same account switched to Polish
reads Polish on every one of those surfaces. **Increment 1, on `master` before 2026-09-14.**

The rhythm e-mail phrased in the account's language is **increment 2**, after the submission, so the
protected loop is not touched inside the freeze window.

### Secondary

- Switching language mid-session re-renders every surface without a reload.
- Proposals keep the user's own words verbatim whatever the UI language — a Polish dream quoted inside
  an English sentence reads as intended, never translated.
- New entries are detected as Polish or English when typed.

### Guardrails

- **The natural-rhythm loop keeps firing** — scheduler, e-mail delivery and the four responses are
  not disturbed. Increment 2 touches the e-mail body only, never the trigger.
- **No Polish word reaches an English account, and vice versa.** A leaked untranslated string is a
  regression, not a cosmetic bug. This includes the text fallback proposal, which sits on the
  on-demand path when the model is unavailable.
- **Existing Polish accounts keep Polish without doing anything.** The default flips only for new
  accounts; nobody wakes up to an English app.

## Timeline acknowledgment

Scoped down on 2026-09-03: increment 1 (~1 week of evenings) before the 2026-09-14 submission, inside
the roadmap's 09-11 freeze; increment 2 after. No sustained-effort override recorded — the user chose
scope-down over commit.

## Functional Requirements

### Language selection

- FR-001: A visitor sees sign-in and sign-up in their browser language, English when unmatched, with a visible language switch on those screens. Priority: must-have. Change: new
  > Socrates: Counter-argument considered: "a visible switch on the auth screen beats detection — Polish users on English-OS laptops would otherwise hunt for a switch they haven't got yet." Resolution: revised — detection stays as the default, and the auth screens gain an explicit switch.
- FR-002: A user can set the account's preferred language (Polish or English). Priority: must-have. Change: new
  > Socrates: Counter-argument considered: "browser language alone is enough; a stored setting is extra state." Resolution: stands — the setting must also drive e-mails, and an e-mail has no browser to ask.
- FR-003: A new account starts in the language its sign-up screen was shown in. Priority: must-have. Change: new
  > Socrates: Counter-argument considered: "ask explicitly at sign-up instead of inheriting a silent guess." Resolution: stands, with a condition — the account switch (FR-002) ships in increment 1 so the inherited language is changeable before the first proposal.
- FR-004: An existing account keeps Polish with no action from the user. Priority: must-have. Change: preserved
  > Socrates: Counter-argument considered: "there is one real existing user; set it by hand." Resolution: stands — the guardrail says nobody wakes up to an English app.

### Surfaces — increment 1 (before 2026-09-14)

- FR-005: A user sees the shell, goals view, forms, filters and account menu in the account language. Priority: must-have. Change: new
  > Socrates: Counter-arguments considered: "every string now has two texts to keep in step"; "dates, plurals and 'idle for N days' must follow too". Resolution: stands as written.
- FR-006: A user sees the 11 category names in the account language. Priority: must-have. Change: modified
  > Socrates: Counter-arguments considered: "the category API's `name` becomes language-dependent, making the reserved Vary/Accept-Language path live behaviour"; "English names for the 11 domains do not exist yet". Resolution: stands as written.
- FR-007: A user requesting a proposal receives the proposal, its first step and its four responses in the account language, with their own entry text quoted verbatim. Priority: must-have. Change: modified
  > Socrates: Counter-arguments considered: "English output quality is unverified — prompts were only tuned on Polish"; "a Polish entry quoted verbatim inside English reads odd"; "the AI memory is Polish, so reasoning mixes languages". Resolution: stands as written.
- FR-008: When the model is unavailable, the text fallback proposal is in the account language — it sits on the same on-demand path. Priority: must-have. Change: modified
  > Socrates: Counter-arguments considered: "the fallback is rare; defer to increment 2"; "a second `phrase` implementation is a lot for a rare path". Resolution: stands as written — the guardrail forbids any leaked string, rare or not.

### Surfaces — increment 2 (after submission)

- FR-009: A user receives the natural-rhythm e-mail in the account language. Priority: must-have. Change: modified
  > Socrates: Counter-arguments considered: "an English account receiving a Polish e-mail in the gap between increments breaks the leak guardrail"; "deliverability is language-sensitive". Resolution: stands in increment 2. The inter-increment gap is logged under Open Questions.

### Nice-to-have

- FR-010: Switching language re-renders every surface without a reload. Priority: nice-to-have. Change: new
  > Socrates: Counter-arguments considered: "a full reload on switch is fine; drop it"; "promote to must-have — a reload feels broken in a PWA". Resolution: stands as nice-to-have.
- FR-011: A new entry is detected as Polish or English when typed. Priority: nice-to-have. Change: new
  > Socrates: Counter-arguments considered: "nothing consumes the detected language yet — speculative data"; "keep, but name the consumer". Resolution: stands as nice-to-have.

### Preserved

- FR-012: The natural-rhythm loop selects, sends and records responses exactly as today. Priority: must-have. Change: preserved
  > Socrates: Counter-arguments considered: "'exactly as today' is too strong once FR-009 changes the e-mail body"; "it blocks fixing anything in the loop". Resolution: stands as written.

## User Stories

### US-01: An English-reading visitor gets a proposal without meeting Polish

- **Given** a visitor whose browser language is English and who has no account
- **When** they sign up, add a dream, and press the proposal button
- **Then** every screen, the 11 category names, the proposal, its first step and the four response labels are in English, and their dream text is quoted verbatim inside the proposal

#### Acceptance Criteria

- Sign-in and sign-up render in English before any account exists (FR-001)
- The new account's preferred language is English without the visitor choosing it (FR-003)
- Shell, goals view, forms, filters and account menu carry no Polish string (FR-005)
- Category names are the English names, in the same `display_order` (FR-006)
- The proposal and first step are English prose; the dream text appears unchanged (FR-007)
- With the model unavailable, the fallback proposal is English (FR-008)
- A Polish-browser visitor walking the same path reads Polish on every surface

## Business Logic

**No domain logic change. This is an infrastructure/technical change** — language is a presentation
concern.

The existing rule is untouched: the app itself, at an irregular rhythm, reminds the user of neglected
goals and dreams, choosing what, when and from which life domain, based on what it remembers about
them. Selection, rhythm and category balancing consume the same inputs and produce the same choice;
only the language in which the choice is phrased — and in which every screen addressed to the person
is written — follows the account (after login) or the browser (before), with English when unknown.

## Constraints & Preserved Behavior

- **`openapi.yaml` stays additive** — no field renamed or removed. `name` on the category resource
  keeps its meaning (the name in the language the server picked); language-related fields are
  additions.
- **Flyway stays expand-only** — any new column is nullable or defaulted; no existing row is rewritten,
  so an image rollback stays safe.
- **Prompts stay English and only *name* the output language** — the existing rule from the project
  guide; a Polish (or English-demonstrating) prompt is the bug.
- **Existing-system constraints:** the roadmap's **2026-09-11 code freeze** — only submission-blocking
  fixes after it, so increment 1 is merged and verified on production by then; the full gate
  (`/check`) and the living-documentation walk (`docs/index.html`, `openapi.yaml`) are part of the
  slice, not follow-up; **production is verified by hand on `2doai.app` in both languages**, the same
  walk as on 2026-09-03, once per language.
- **Preserved behavior:** the natural-rhythm loop (trigger, selection, e-mail delivery, response
  recording) works exactly as today throughout increment 1 (FR-012); existing accounts read Polish
  without acting (FR-004); users' entry text is quoted verbatim, never translated (FR-007).
- **Data migration:** the language lives **on the account** — one defaulted column; existing rows read as
  Polish. Entries, AI memory and the category table are not rewritten. (A client-only language was
  considered and rejected on 2026-09-04: it cannot drive e-mails or keep existing accounts Polish.)

## Non-Functional Requirements

- Switching language takes effect within one page load — no surface shows the previous language after
  the switch.
- Language choice does not noticeably change response timing: CRUD actions still show a visible effect
  in under 500 ms, in either language.
- Both languages are usable on the latest two major versions of the mainstream browsers, as today.

## Non-Goals

- **No translation of stored entries or AI memory.** What users typed and what the AI remembers stay
  in their original language; the change touches what the app says, never what the user said.
- **No per-entry language tags driving behavior.** FR-011 stays a nice-to-have; nothing in this change
  consumes a detected entry language.

## Open Questions

1. **The inter-increment gap:** an English account created in increment 1 may receive a Polish rhythm
   e-mail before increment 2 lands — a direct conflict with the "no leaked word" guardrail. Suppress
   e-mails for non-Polish accounts until FR-009, accept the gap, or pull FR-009 forward? Owner: author.
   By: before increment 1 merges.
2. **English names for the 11 life domains** do not exist yet (FR-006); "Sprawy formalne i
   administracyjne" and "Rozwój wewnętrzny / wartości" need product naming, not translation. Owner:
   author. By: increment 1.
3. **English proposal quality** (FR-007) is unverified — prompts were tuned on Polish and the memory of
   the existing account is Polish. Resolved only by the by-hand production walk in English. Owner: author.

## Quality cross-check

All six brownfield elements present — soft gate passed on 2026-09-04, no gaps to mirror into Open Questions.

- Access Control: present (no auth change; account language setting)
- Business Logic: present ("No domain logic change" — valid for an infrastructure change)
- Project artifacts: present
- Timeline-cost acknowledgment: present (scoped down to ~1 week before 2026-09-14)
- Non-Goals: present (2 entries)
- Preserved behavior: present (rhythm loop, Polish default, verbatim entries, additive API, expand-only schema, 09-11 freeze)

## Forward: stack-assess (informational — out of PRD scope)

> Repo-derived seams the downstream step should start from — not decisions.

- Backend already separates *instruction language* from *output language* (`ProposalPrompt.OUTPUT_LANGUAGE`);
  `ProposalTemplate.phrase` and `ProposalEmail` are the two Polish-bound server surfaces named in code.
- `CategoryController` reserved `Accept-Language` / `Vary` for the day a second language is seeded;
  `name` is already language-neutral on the wire.
- The SPA has no i18n dependency; copy is hardcoded Polish and `<html lang="pl">` is static. The
  project guide says a second locale of the fallback proposal is "a second implementation of `phrase`".
- Account-level language column is expand-only Flyway per the persistence rules.
