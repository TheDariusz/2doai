# Polish + English Localization — Plan Brief

> Full plan: `context/changes/pl-en-localization/plan.md`
> Product framing: `context/foundation/prd-v2.md` and `context/foundation/shape-notes.md`

## What & Why

Anyone who does not read Polish cannot use 2do AI: a person shown the app, a reviewer of the
submission or the public repository, a future signup. The change makes the app speak **English and
Polish**, English-first, with Polish preserved as a language rather than as the default. The
delivered thing is the locale *mechanism*; English copy is what rides on it.

## Starting Point

The product is Polish-only in its copy, its generated text and its e-mails — but was built with this
day anticipated. Four seams already exist: the category API serves a language-neutral `name`, the AI
prompt *names* its output language instead of being written in it, and the SPA renders whatever the
server sends. What is actually Polish-bound is smaller than the feature name suggests: **24 backend
literals in two files**, 11 seeded category names, and ~86 frontend strings. The real cost is
elsewhere — ~169 test assertions query by Polish accessible name.

## Desired End State

An English-reading visitor signs up on `2doai.app` and reads the shell, the goals view, the forms,
the filters and the 11 category names in English; adds a dream; requests a proposal; and gets the
proposal and its first step as English prose with their own words quoted verbatim. The same account
switched to Polish reads Polish everywhere, without a reload. An account that existed before the
change reads Polish and was never asked anything.

## Key Decisions Made

| Decision | Choice | Why | Source |
| -------- | ------ | --- | ------ |
| Locale transport | `Accept-Language` header; account column persists | One line at `client.ts:41` covers all 10 call sites, and `openapi.yaml` already commits to exactly this design in prose | Plan |
| Read path | `language` rides on `UserPrincipal` | Keeps `GET /api/users/me` at **zero database queries**, the property that lets the metered Neon compute autosuspend | Plan |
| Category labels | `name_en` column | The path `CategoryController`, `openapi.yaml` and `data-model.md` all name; wire contract unchanged | Plan |
| Frontend i18n | i18next + react-i18next | Chosen over a hand-rolled catalog; nothing to get subtly wrong, and a third locale costs nothing | Plan |
| Test strategy | Migrate all ~169 assertions to English | Chosen over pinning the suite to `pl`; the suite then exercises the locale most users see | Plan |
| Leak guard | `CustomTypeOptions` key parity + ESLint non-ASCII ban | A missing translation cannot compile and new hardcoded copy cannot pass `/check` | Plan |
| Fallback proposal | A second implementation of `phrase`, not extra branches | Already prescribed by CLAUDE.md and by the class's own test javadoc | Project guide |
| E-mail gap (FR-009) | Accept the gap; FR-009 stays increment 2 | Author's call; see the risk below | Plan |
| 11 English domain names | Descriptive set mirroring Polish scope | The selection engine balances across domains, so the two locales must categorize identically | Plan |

## Scope

**In scope:** account language column + `PATCH /api/users/me`; `Accept-Language` resolution; English
category names; the AI prompt's language parameter; an English fallback proposal; the full SPA
catalog with both switches; the test migration; the living-documentation walk and a production walk
per language.

**Out of scope:** the natural-rhythm e-mail (FR-009, increment 2); translating stored entries or AI
memory; per-entry language detection (FR-011); localized API `detail` prose; `ProposalRhythm`'s
hardcoded `Europe/Warsaw`; the parked `category-contract-guards` work.

## Architecture / Approach

The SPA sends `Accept-Language` on every request and the server renders in it; the stored account
column is what the SPA seeds itself from at login and what e-mails will read when FR-009 lands. The
language rides on `UserPrincipal` so the hot read path stays query-free, and the switch endpoint
rebuilds the principal in the session — cheap, because sessions are in-memory on one Fly machine.
Backend first (column and transport, then each generated-text surface), then the SPA, then docs.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| ----- | ---------------- | -------- |
| 1. Account language and transport | `V10` column, `AppLanguage`, principal field, `PATCH /users/me` | The switch must refresh the principal in-session, or `/me` serves stale data until re-login |
| 2. Category names | `V11` + `name_en`, per-language controller, `Vary` | The one-hour private cache serving one language's response to the other's caller — a bug a single-language suite cannot see |
| 3. Proposal text | Prompt language parameter, English fallback sibling | English output quality is unverified; only the production walk resolves it |
| 4. Frontend i18n | i18next, ~86 keys, both switches, lint guard | Three sentence-composition hazards in `ProposalCard.tsx` must be restructured, not translated |
| 5. Test migration | ~169 assertions to English, new coverage | The largest single block of work, and the likeliest source of a merge-blocking mistake |
| 6. Docs and production walk | The nine-surface walk, ER re-export, both-language walk | A partial docs pass reads exactly like a complete one |

**Prerequisites:** none outstanding — Linear DEV-49 is open and In Progress, due 2026-09-11.
**Estimated effort:** ~6–7 evenings across 6 phases. Phases 4 and 5 must land in one PR — extraction
leaves the frontend suite red by construction until the migration closes it.

## Open Risks & Assumptions

- **The accepted gap is wider than the PRD framed it.** Scheduled proposal text is generated once at
  send time and stored, so until FR-009 lands an English account gets Polish in the e-mail *and* on
  the pending card in the SPA. Confined to one line in `ProposalService.proposeScheduled` and pinned
  by a test; pulling FR-009 forward stays cheap, since the scheduler already loads the account row.
- **The code freeze is 2026-09-11**, seven evenings from planning. Phase 5 is the compressible one:
  pinning the suite to `pl` instead would remove ~169 edits if the schedule tightens.
- **English proposal quality is unverified** — prompts were tuned on Polish and the existing
  account's AI memory is Polish, so reasoning may mix languages. PRD Open Question 3, resolvable
  only by the by-hand walk.
- **The category labels remain unguarded across the stack**; this change adds an English copy to the
  same six places `lessons.md` already flags.

## Success Criteria (Summary)

- An English-reading visitor completes sign-up → dream → proposal → first step without meeting a
  Polish word, verified by hand on `2doai.app`.
- The same account switched to Polish reads Polish on every one of those surfaces, without a reload.
- The pre-existing account still reads Polish, having been asked nothing.
