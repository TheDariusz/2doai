# Polish + English Localization Implementation Plan

## Overview

Give 2do AI a second language. Every surface addressed to a person — the SPA shell, the 11 category
names, the AI-phrased proposal and its model-free fallback — is rendered in English or Polish,
chosen by the account after login and by the browser before it, with English as the fallback. The
delivered thing is the locale *mechanism*; English copy is what rides on it.

Upstream framing is settled and is not re-opened here: `context/foundation/prd-v2.md`
(FR-001…FR-012, guardrails, two increments) and `context/foundation/shape-notes.md`. This plan owns
solution design only.

## Current State Analysis

The system is Polish-only in its copy, its generated text and its e-mails, and was built with the
second language anticipated in four named places but implemented in none.

**Seams already in place (deliberate, documented):**

- `CategoryController` serves `name`, not `name_pl` — "the label to show this caller, in the language
  the server picked" (`CategoryController.java:71-78`, mirrored in `openapi.yaml:756`). The wire
  contract does not have to change at all.
- `ProposalPrompt` *names* the output language to the model rather than demonstrating it —
  `OUTPUT_LANGUAGE = "Polish"` interpolated into two English personas (`ProposalPrompt.java:50`),
  with a javadoc promising "this becomes a parameter threaded from the caller, and nothing else in
  the class changes."
- The SPA renders whatever the server sends and never picks a locale itself
  (`AppLayout.tsx:6-16`).

**What is actually Polish-bound, measured:**

| Surface | Size |
| ------- | ---- |
| `ProposalTemplate.java` | 22 literals, including a hand-rolled 3-form Polish plural (`:70-77`) |
| `ProposalEmail.java` | 2 literals (`:41`, `:50`) |
| `category.name_pl` | 11 seeded rows (`V2__seed_categories.sql`) |
| Frontend copy | ~86 unique keys across 7 files; `GoalsPage.tsx` (42) and `ProposalCard.tsx` (22) are 71% |
| Frontend tests | ~169 assertion sites querying by Polish accessible name, across 6 files |

**Constraints discovered that shape the design:**

- `GET /api/users/me` is answered entirely from the session principal and makes **zero database
  queries** (`UserController.java:78`, `UserResponse.from(UserPrincipal)`). This is deliberate: every
  avoided query is idle time the metered Neon compute can autosuspend through
  (`context/foundation/lessons.md`, "Let a scale-to-zero database actually sleep").
- `CategoryController` reads the 11 rows **once in its constructor** and holds them in a field
  (`:32-36`), for the same reason.
- `User` has **no setters at all** and no mutator endpoint exists — the only `PUT`/`PATCH` in the
  whole backend is `GoalController.java:64`. `next_proposal_at` is moved by a targeted repository
  update, which is the house pattern to copy.
- No migration in the project uses a SQL `DEFAULT` clause; the idiom is nullable-plus-meaning
  ("null means X"), as in `V9__scheduler_state.sql:19`.
- The SPA has three runtime dependencies and no i18n infrastructure of any kind.

### Key Discoveries

- `client.ts:41` already sets two headers centrally for all 10 call sites — `Accept-Language` is a
  one-line diff there (`api/client.ts:36-81`).
- `ProposalTemplateTest`'s javadoc already prescribes the design: *"A second locale gets its own copy
  of this class, not extra cases here."* CLAUDE.md agrees: "a second locale is a second
  implementation of `phrase`."
- `ProposalScheduler.java:208` already loads the account row before sending
  (`users.findById(accountId).ifPresent(...)`), so the account's language is in hand at the send site
  whenever FR-009 is pulled forward.
- `ApiSurfaceTest` compares controller path patterns against `openapi.yaml` in both directions — a
  new endpoint is red until the spec has it. Free enforcement of the living-documentation rule.
- **Three genuine i18n hazards, all in `ProposalCard.tsx`**: `Nie udało się ${what}` composes a
  sentence from genitive fragments (`:64` with `:137`, `:150`, `:173`); `Za {days} dni` (`:241`) is
  grammatically correct only because 7/30/90 happen to share a form; and `:50` quotes another
  string by name (`„Pokaż wycofane”`, from `GoalsPage.tsx:329`), so the two must move as a pair.
- Nothing guards the category label list today — no test asserts a Polish category name except
  `CategoryApiTest:33`, and `context/foundation/lessons.md` already names the `LifeDomain` codes as
  unguarded across six copies.

## Desired End State

A visitor whose browser is English lands on `2doai.app`, signs up, and reads the shell, the goals
view, the forms, the filters, the account menu and the 11 category names in English. They add a
dream, press the proposal button, and the proposal and its first step come back as English prose
with their own words quoted verbatim. With the model unavailable, the fallback sentence is English
too. The same account switched to Polish reads Polish on every one of those surfaces, without a
reload. An account that existed before this change reads Polish and was never asked anything.

Verified by: the full `/check` gate green; a new English-path test suite; and a by-hand walk on
production, once per language, matching the 2026-09-03 walk.

## What We're NOT Doing

- **Not translating stored entries or AI memory.** What the user typed and what the AI remembers
  stay in their original language (PRD Non-Goals).
- **Not localizing the natural-rhythm e-mail** — FR-009 stays in increment 2. See the accepted
  exception below.
- **Not detecting the language of new entries** (FR-011, nice-to-have, no consumer).
- **Not localizing API `detail` prose.** Problem responses stay English; the SPA maps status codes
  and URNs to its own copy, and `client.ts:14` states `detail` "is prose and must not be parsed".
- **Not touching `ProposalRhythm.USER_ZONE`** (`Europe/Warsaw`, `:35`). It is the timezone twin of
  this change and out of scope; an English account still gets daytime-hours delivery in Warsaw time.
- **Not fixing the unguarded `LifeDomain` code duplication** — `context/changes/category-contract-guards`
  owns that and stays parked.
- **Not adding a third locale, message-catalog tooling, or a translator workflow.**

### The accepted exception, stated plainly

FR-009 stays in increment 2, decided 2026-09-04. The consequence is **wider than PRD Open Question 1
described**: proposal text is generated once at send time and stored on the `proposal` row, so an
English account whose scheduled proposal fires before increment 2 gets Polish in the e-mail *and* on
the pending card in the SPA (`GET /api/proposals/pending` → `ProposalCard.tsx:194`). This is a
knowing, documented departure from the "no leaked word" guardrail, confined to
`ProposalService.proposeScheduled` and pinned by a test so it cannot drift into other paths
unnoticed. Pulling FR-009 forward later is small — the scheduler already holds the account row.

## Implementation Approach

`Accept-Language` is the transport; the account column is the persistence. The SPA sends the header
on every request and the server renders in it; the stored column is what the SPA seeds itself from at
login and what e-mails will read when FR-009 lands. This is exactly the design `openapi.yaml:756`
already commits to in prose, so the wire contract gains fields and never changes meaning.

The language rides on `UserPrincipal` as a fourth component so `GET /api/users/me` keeps its
zero-query property. The switch endpoint rebuilds the principal in the session, which is cheap
because sessions are in-memory on one Fly machine.

Order: the backend gains the column and the transport (Phase 1), then each generated-text surface
follows it (Phases 2–3), then the SPA (Phases 4–5), then documentation and the production walk
(Phase 6).

## Critical Implementation Details

**Principal refresh is the one lifecycle trap.** `PATCH /api/users/me` writes the column, but the
session still holds the old `UserPrincipal`. Unless the handler replaces the `Authentication` in the
`SecurityContext` (and lets Spring Security persist the mutated context to the session), the user
switches language and `/me` keeps reporting the old one until they log out. Write the test for this
before the handler.

**The category cache and `Vary` interact in the one way a single-language test suite cannot see.**
`CategoryController`'s own javadoc says it: "A cache keyed on the URL alone would hand the Polish
response to a caller who asked for English." The `Cache-Control: max-age=3600, private` header stays,
so `Vary: Accept-Language` must ship in the *same* commit as the second language — not after.
Assert the header, not just the body.

**`ProposalCard.tsx` sentence composition must be broken up, not translated.** `Nie udało się
${what}` plus three genitive fragments is grammar-by-concatenation. It becomes three complete
sentence keys. Translating the template and the fragments separately produces valid-looking English
built on a Polish sentence shape.

**Phases 4 and 5 leave the frontend suite red between them.** Extracting copy behind `t()` breaks
~169 assertions by construction; Phase 5 is what closes them. Land both in one PR so `master` is
never merged red, and treat Phase 4's gate as `tsc` + `lint` only.

**Re-exporting the ER diagrams needs the known draw.io fix.** The schema changes twice here
(`app_user.preferred_language`, `category.name_en`), so both `.drawio` files and both `.svg` exports
move. The draw.io CLI always writes `color-scheme: light dark` into the SVG, which turns
`docs/index.test.mjs` red; hand-fix it to the light palette after each export.

---

## Phase 1: Account language and its transport

### Overview

The backend learns what language a request wants, and remembers what language an account prefers,
without costing a database query on the hot path.

### Changes Required:

#### 1. Migration

**File**: `backend/src/main/resources/db/migration/V10__account_language.sql`

**Intent**: Give the account a preferred language, expand-only, so an image rollback stays safe and
no existing row is rewritten — FR-004 is satisfied by the column's absence of a value, not by a
backfill.

**Contract**: `ALTER TABLE app_user ADD COLUMN preferred_language VARCHAR(2);` — nullable, no
`DEFAULT`, following `V9__scheduler_state.sql:19`. Null means "never chosen; read as Polish", and
the file's comment block must say so, as V9's does.

#### 2. The language type

**File**: `backend/src/main/java/com/thedariusz/todoai/user/AppLanguage.java`

**Intent**: A two-valued domain type so no layer passes a bare string around, and so the
"unrecognised means English" rule lives in one place.

**Contract**: `enum AppLanguage { PL, EN }` with a factory from `java.util.Locale` and a constant for
the fallback (`EN`). It also answers the name the model is told to write in ("Polish" / "English"),
so Phase 3 has one source for that word. Persisted via its `name()` in the `VARCHAR(2)` column.

#### 3. Request language resolution

**File**: `backend/src/main/java/com/thedariusz/todoai/security/` (or a small `i18n` package —
implementer's call; one class either way)

**Intent**: Resolve the language of the current request from `Accept-Language`, defaulting to English
when the header is absent or matches neither locale.

**Contract**: Configure Spring's `AcceptHeaderLocaleResolver` with supported locales `en`, `pl` and
default `en`. Use the framework's resolver rather than parsing the header — it already handles
quality values and malformed input.

> **Amended after the Phase 1 review (2026-09-04).** Built as `i18n/LocaleConfig`, holding only the
> resolver bean. Handlers declare a plain `Locale` parameter — Spring MVC resolves it through that
> bean — and convert with `AppLanguage.of(locale)`, so the single conversion point is item 2's
> factory and there is no request-scoped static to read. `LocaleContextHolder` appears nowhere in the
> backend. Chosen because passing the language down as an argument keeps the services that render
> text testable without a servlet, and because `LocaleContextHolder.getLocale()` falls back to the
> JVM's default locale outside a request — non-deterministic across machines. Phases 2–3 follow this
> shape.

#### 4. The principal carries the language

**Files**: `security/UserPrincipal.java`, `security/AppUserDetailsService.java`,
`auth/UserResponse.java`

**Intent**: Let `GET /api/users/me` report the account's language while keeping its zero-query
property intact.

**Contract**: `UserPrincipal` gains a fourth component of type `AppLanguage`, populated by
`AppUserDetailsService` from the `User` row it already loads at login (null column → `PL`).
`UserResponse` gains a `language` field, produced by both its factories. This is the field the SPA
seeds itself from.

#### 5. The account setting endpoint

**Files**: `user/UserController.java`, `user/UserRepository.java`, a new request DTO in `auth/`

**Intent**: Let a user change the account's language (FR-002) and have the change visible on the
current session immediately, not after re-login.

**Contract**: `PATCH /api/users/me` taking `{ "language": "PL" | "EN" }`, returning the updated
`UserResponse`. The write is a targeted repository update, mirroring how `next_proposal_at` moves —
`User` gains no setter. **The handler must then replace the `Authentication` in the
`SecurityContext` with one carrying a rebuilt `UserPrincipal`**, or the session serves stale data
(see Critical Implementation Details). Validation follows the split the house already pins: a value outside
the enum never deserializes, so it is a **400**, while an omitted `language` fails `@NotNull` and is
a **422** via the existing `ApiExceptionHandler`.

> **Amended after the Phase 1 review (2026-09-04).** This plan originally said 422 for anything
> outside the enum. That is not reachable without a custom deserializer, and forcing it would make
> `/users/me` the one endpoint answering a bad enum differently from every goal endpoint —
> `GoalApiTest.rejectsUnknownWireLiteralsWith400` and `openapi.yaml` both document the 400/422 split.
> The implementation follows the house rule and pins both halves.

#### 6. Registration inherits the sign-up screen's language

**File**: `auth/RegistrationService.java`

**Intent**: FR-003 — a new account starts in the language its sign-up screen was shown in — with no
new request field.

**Contract**: `User`'s constructor takes the resolved `AppLanguage` alongside email and password
hash. The sign-up screen's language *is* the `Accept-Language` the sign-up request carried, because
the SPA sets that header from whatever it is currently rendering (including after the auth-screen
switch). No change to `RegisterRequest`.

#### 7. Specification

**File**: `context/foundation/openapi.yaml`

**Intent**: Publish the new endpoint and field, and the header that now changes responses. The spec
moves in the same commit as the Java.

> **Amended after the Phase 1 review (2026-09-04).** The claim "`ApiSurfaceTest` fails until it does"
> was false as written: that test compared **path sets**, and `/users/me` already existed for `GET`
> and `DELETE`, so the whole `PATCH` could have shipped undocumented with the suite green. The test
> was widened to compare **method + path** operations in both directions, which makes the
> enforcement real — and matters for Phases 2–3, which likewise add to paths that already exist.

**Contract**: A `PATCH /users/me` path with `operationId: updateCurrentUser` and the `XsrfToken`
parameter every mutating operation declares; a `language` property on the `User` schema
(`x-extensible-enum` with `PL`, `EN`, matching the house style for open-ended enums); a reusable
`AcceptLanguage` header parameter; and a version bump from `1.5.0`.

### Success Criteria:

#### Automated Verification:

- Backend suite passes: `cd backend && mvn test`
- `ApiSurfaceTest` green — the spec has `PATCH /users/me`
- Hibernate `ddl-auto=validate` boots green against V10 (any `mvn test` run proves it)
- New `AuthApiTest` cases: registering with `Accept-Language: en` yields `language: "EN"` on `/me`;
  an account row with a null column reads `PL`; `PATCH` changes it and **the same session** sees the
  new value on `/me` without re-login
- `UserRepositoryTest` covers the targeted language update

#### Manual Verification:

- Register from a browser set to English and confirm `/me` reports `EN`
- Switch via `PATCH`, reload the SPA, confirm the value survives

**Implementation Note**: After completing this phase and all automated verification passes, pause
here for manual confirmation before proceeding.

---

## Phase 2: Category names in two languages

### Overview

The 11 life domains gain English names, and `/api/categories` starts answering in the caller's
language — the behaviour `openapi.yaml` has described in the future tense since the table was
seeded.

### Changes Required:

#### 1. Migration

**File**: `backend/src/main/resources/db/migration/V11__category_name_en.sql`

**Intent**: Add the English label column and seed the 11 names, expand-only.

**Contract**: `ALTER TABLE category ADD COLUMN name_en VARCHAR(255);` followed by 11 `UPDATE`
statements keyed on `code`. Nullable rather than `NOT NULL` so the previous image still boots
against the new schema. The confirmed names:

```
HEALTH        Health                          FINANCE    Finances
CAREER        Career & professional growth    EDUCATION  Education & personal growth
RELATIONSHIPS Relationships                   HOME       Home & surroundings
LEISURE       Leisure & hobbies               ADMIN      Admin & paperwork
SAFETY        Safety & preparedness           TRANSPORT  Transport & mobility
INNER_GROWTH  Inner growth & values
```

#### 2. Entity and response

**File**: `backend/src/main/java/com/thedariusz/todoai/category/Category.java`

**Intent**: Map the new column and let the response pick a label by language.

**Contract**: A `nameEn` field mapped to `name_en`, plus a getter. `CategoryResponse.from` takes an
`AppLanguage` and fills the wire field `name` from the matching column. The wire shape is unchanged.

#### 3. Controller

**File**: `backend/src/main/java/com/thedariusz/todoai/category/CategoryController.java`

**Intent**: Serve per-language without reintroducing a per-request query, and stop the existing
one-hour private cache from serving one language's response to the other's caller.

**Contract**: The single startup-built `collection` field becomes a per-language map built the same
way in the constructor (two immutable collections, still zero queries at request time). The response
adds `Vary: Accept-Language` beside the existing `Cache-Control`. Update the javadoc at `:45-48`,
which currently describes this as future work.

#### 4. Specification

**File**: `context/foundation/openapi.yaml`

**Intent**: The `name` description predicts this change; it must now describe it.

**Contract**: Add the `Vary` response header and the `Accept-Language` parameter to `GET
/categories`; rewrite the `Category.name` description (`:756`) from "Polish today, because it is the
only one seeded" to what is now true. `display_order`'s `maximum: 11` and the `x-extensible-enum`
code list are untouched.

### Success Criteria:

#### Automated Verification:

- Backend suite passes: `cd backend && mvn test`
- `CategoryApiTest`: `Accept-Language: en` returns `Health` for `HEALTH`; `pl` returns `Zdrowie`; no
  header returns English
- `CategoryApiTest` asserts `Vary` contains `Accept-Language` alongside the existing `Cache-Control`
  assertion at `:46`
- `CategorySeedTest` asserts all 11 rows have a non-blank `name_en`, mirroring
  `everyPolishNameIsNonBlank`

#### Manual Verification:

- `curl` the endpoint with each header value and read the 11 names
- Confirm the browser does not serve a cached Polish response after switching (the `Vary` check)

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 3: Generated proposal text follows the account

### Overview

The two places the backend writes prose for a person — the LLM prompt and the model-free fallback —
learn which language to write in.

### Changes Required:

#### 1. The prompt names the caller's language

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalPrompt.java`

**Intent**: Replace the hardcoded constant with a parameter, exactly as the class javadoc at `:41-47`
promises. The prompts stay English and continue to *name* the output language rather than
demonstrate it — the CLAUDE.md rule.

**Contract**: `OUTPUT_LANGUAGE` disappears; `forProposal` and `forFirstStep` take an `AppLanguage`
and interpolate its English name ("Polish" / "English") into the two personas. The `.formatted(...)`
call sites move from the static field initialisers into the methods. Remove the `ponytail:` comment
at `:49-50` — the upgrade it names is this change.

#### 2. The fallback gets its second implementation

**Files**: `proposal/ProposalTemplate.java` (+ a per-locale sibling)

**Intent**: A second locale of the fallback sentence, as its own javadoc and CLAUDE.md both
prescribe — a second implementation of `phrase`, not extra branches in the Polish one.

**Contract**: The Polish class keeps its 22 literals, its locative month names and its 3-form plural
untouched. The English sibling implements the same `phrase(Goal, long)` signature; English needs no
plural table beyond singular/plural and no case inflection, so it is markedly smaller. A small
dispatch on `AppLanguage` picks one. `ProposalTemplateTest` is unchanged; the sibling gets its own
test class.

#### 3. Threading the language through the service

**File**: `backend/src/main/java/com/thedariusz/todoai/proposal/ProposalService.java`

**Intent**: The on-demand path (`propose`, and the first-step call inside `answer`) writes in the
request's language. The scheduled path deliberately does not.

**Contract**: `propose()` and `answer()` resolve the language from the request and pass it to
`ProposalPrompt` and to the fallback dispatch in `draft(...)` (`:252-264`). **`proposeScheduled(UUID)`
passes `AppLanguage.PL` unconditionally** and carries a comment naming this as the accepted FR-009
exception with a pointer to `change.md`. That single line is the whole of the gap.

### Success Criteria:

#### Automated Verification:

- Backend suite passes: `cd backend && mvn test`
- `ProposalPromptTest` asserts the word `Polish` for `PL` and `English` for `EN` in both system
  messages (extending the existing assertion at `:170-175`)
- A new test class for the English fallback covers the sentence, the elapsed-days phrasing, the
  singular/plural boundary and the passed-deadline case
- `ProposalApiTest`: a request with `Accept-Language: en` whose model call fails returns
  `source: "TEMPLATE"` with an English sentence
- A test pinning `proposeScheduled` to Polish regardless of the account's language, so the accepted
  gap cannot silently widen or silently close

#### Manual Verification:

- Request a proposal in English against the real model and read the sentence and first step — this
  is the only way to resolve PRD Open Question 3 (English output quality is unverified)
- Confirm a Polish entry quoted inside an English proposal reads as intended, not translated

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 4: Frontend i18n and catalog extraction

### Overview

The SPA gains i18next, a two-locale catalog, and the two language switches. This phase and Phase 5
land in one PR — see Critical Implementation Details.

### Changes Required:

#### 1. Dependencies and configuration

**Files**: `frontend/package.json`, a new `frontend/src/i18n/` module

**Intent**: Install i18next + react-i18next and configure two locales with English as the fallback.

**Contract**: `i18next` and `react-i18next` as runtime dependencies. Config declares `pl` and `en`
resources, `fallbackLng: 'en'`, and `interpolation.escapeValue: false` (React escapes already).
Detection order pre-login is browser language then the English fallback; a stored choice from the
auth-screen switch wins over detection.

#### 2. Compile-time key parity

**File**: `frontend/src/i18n/i18next.d.ts`

**Intent**: Make a missing translation a build failure rather than a silent fallback — this is the
leak guard, and it is the reason `en` and `pl` cannot drift.

**Contract**: Augment `CustomTypeOptions` with `resources: { translation: typeof pl }`, and type the
`en` bundle against the same shape so `tsc` rejects a missing or misspelled key on either side.
`npm run build` already runs `tsc -b`, so `/check` enforces it.

#### 3. The catalog

**Files**: `frontend/src/i18n/pl.ts`, `frontend/src/i18n/en.ts`

**Intent**: Hold the ~86 keys extracted from the 7 files below.

**Contract**: A nested object keyed by area (`goals`, `proposal`, `auth`, `account`, `layout`).
Three hazards must be resolved during extraction, not carried across:
- The three `Nie udało się ${what}` call sites become three complete sentence keys.
- `Za {days} dni` becomes an i18next plural key (`_one`/`_other`), which also fixes the latent
  Polish bug if a 1-day preset is ever added.
- `ProposalCard.tsx:50` and `GoalsPage.tsx:329` are translated as a pair — the first quotes the
  second by name.

#### 4. Extraction

**Files**: `pages/GoalsPage.tsx` (42), `pages/ProposalCard.tsx` (22), `pages/AuthPage.tsx` (12),
`auth/AccountMenu.tsx` (9), `layout/AppLayout.tsx` (3), `App.tsx` (1),
`pages/DomainPlaceholder.tsx` (1)

**Intent**: Replace every user-visible literal with a `t()` call, including `aria-label`s,
`window.confirm` text and select-option labels.

**Contract**: `useTranslation()` at the component level. `AuthPage.tsx`'s existing `COPY` map
(`:8-12`) folds into the catalog directly. `AppLayout.tsx:34`'s `2do AI` is the brand and stays.
Server-provided text — `proposal.message`, `proposal.first_step[]`, and the category `name` — is
rendered verbatim as today and is not a catalog key.

#### 5. The transport and the document language

**Files**: `frontend/src/api/client.ts`, the i18n module

**Intent**: Tell the server what to render in, and tell the browser and assistive tech what the page
is written in.

**Contract**: One line at `client.ts:41` adds `Accept-Language` beside the existing `Content-Type`
and `X-XSRF-TOKEN`. Because `client.ts` is a plain module with no React coupling, it reads the
current language from the i18next instance directly, not from context. Separately,
`document.documentElement.lang` is kept in sync on every language change — it drives screen-reader
pronunciation and the `<input type="date">` display format at `GoalsPage.tsx:425`. The static
`lang="pl"` in `frontend/index.html:2` becomes the initial value only.

#### 6. The two switches

**Files**: `pages/AuthPage.tsx`, `auth/AccountMenu.tsx`

**Intent**: FR-001's visible switch on the auth screens, and FR-002's account setting.

**Contract**: On the auth screens the switch changes the SPA language and persists the choice
locally, so the subsequent sign-up request carries it (which is what Phase 1 reads for FR-003). In
the account menu the switch calls `PATCH /api/users/me` and updates the i18next language on success;
after login the account's `language` from `/me` is authoritative and overrides local detection.
Switching re-renders through react-i18next without a reload, which is FR-010.

#### 7. The stray-literal guard

**File**: `frontend/eslint.config.js`

**Intent**: Stop new hardcoded copy from entering the components at all.

**Contract**: A `no-restricted-syntax` rule refusing string literals containing non-ASCII characters
outside `src/i18n/**` and `src/test/**`. It needs an allowlist for the brand and any deliberate
typographic characters. `npm run lint` already runs in `/check`.

### Success Criteria:

#### Automated Verification:

- Type check and build pass: `cd frontend && npm run build`
- Lint passes, including the new rule: `cd frontend && npm run lint`
- Removing a key from `en.ts` makes `npm run build` fail (verify once by hand, then restore) — this
  is the leak guard's actual proof

#### Manual Verification:

- `npm run dev`, switch on the sign-in screen, confirm both auth screens change without a reload
- Log in and switch from the account menu; confirm every surface follows and the choice survives a
  reload
- Confirm `<html lang>` changes with the switch and the date input's display format follows

**Implementation Note**: `npm test` is expected to be **red** at the end of this phase — Phase 5
closes it. Do not merge between the two.

---

## Phase 5: Test migration to English

### Overview

Move the ~169 assertion sites to the new default locale and add the coverage the new behaviour
needs.

### Changes Required:

#### 1. Test locale

**File**: `frontend/src/test/setup.ts`

**Intent**: Initialise i18next for tests with English as the active language, so the suite exercises
the locale most users will see.

**Contract**: Initialise the real i18n instance (not a mock) with `lng: 'en'` in the existing setup
file, beside the XSRF cookie priming. Using the real catalog means a broken key fails a test rather
than rendering a key name.

#### 2. Assertion migration

**Files**: `pages/GoalsPage.test.tsx` (99), `pages/ProposalCard.test.tsx` (35),
`auth/AccountMenu.test.tsx` (15), `pages/AuthPage.test.tsx` (8), `App.test.tsx` (6),
`layout/AppLayout.test.tsx` (4)

**Intent**: Update every query-by-accessible-name and every copy assertion to the English strings.

**Contract**: Assert the English text, not `t('key')` — the suite's value is that it reads what a
user reads. Start with the four helpers that concentrate the damage: `createForm()`
(`GoalsPage.test.tsx:127`), `editForm()` (`:138`), `section(name)` (`:129-131`) and `filters()`
(`:630`). Polish *fixture* data (goal content such as `'Przebiec półmaraton'`) is user-typed content
and stays Polish — it is not copy.

#### 3. Fixtures

**File**: `frontend/src/test/domains.ts`

**Intent**: The 11 category fixtures mirror what the server now returns to an English caller.

**Contract**: Replace the Polish names with the Phase 2 English names, keeping codes and order. Keep
the header comment's warning that this list is duplicated across the stack with nothing guarding it.

#### 4. New coverage

**Files**: new tests beside the components they cover

**Intent**: Cover what did not exist before this change.

**Contract**: The auth-screen switch changes rendered copy without a remount (FR-001, FR-010); the
account-menu switch issues the `PATCH` and follows the response; a Polish-rendered pass over the
US-01 path so the second locale is not left untested by the default flip; and `client.ts` sends
`Accept-Language` matching the active language.

### Success Criteria:

#### Automated Verification:

- Frontend suite passes: `cd frontend && npm test`
- Lint and build still pass: `cd frontend && npm run lint && npm run build`
- The full gate is green end to end: `/check`

#### Manual Verification:

- Walk the US-01 path in the browser in English, then the same path in Polish
- Confirm no screen shows a raw i18next key anywhere

**Implementation Note**: Pause for manual confirmation before proceeding.

---

## Phase 6: Living documentation and production walk

### Overview

The slice is not done until the reviewer-facing page describes what merged, and until a human has
read both languages on production. Both are part of this slice, not follow-up work.

### Changes Required:

#### 1. The architecture page

**File**: `docs/index.html`

**Intent**: Walk all nine surfaces from CLAUDE.md's Living-documentation list. A partial pass reads
exactly like a complete one, and the docs test cannot see prose that quietly became false.

**Contract**: In order — a **Localization** row in the `#overview` capability table; a row per new
package in `#code-map` if one was added; the `category`, `user + auth` and `proposal` `<h3>` prose
and their class diagrams (`AppLanguage`, `UserPrincipal`'s fourth component, `PATCH /users/me`,
`Category.nameEn`, `ProposalPrompt`'s new signature, the fallback's sibling class); a `#backend`
terms entry for the language concept; `#flows` for language selection before and after login;
`#data` prose for the two new columns; `#roadmap` and `#glossary` status prose; the endpoint list in
the `#backend` note; and the `Verified against …` stamp ending `#overview`.

#### 2. Data model

**Files**: `context/foundation/data-model.md`, `data-model-current.drawio`, `data-model-current.svg`,
`data-model-target.drawio`, `data-model-target.svg`

**Intent**: The schema changed twice, so the canonical diagram and its Internationalization section
must follow.

**Contract**: Add `app_user.preferred_language` and `category.name_en` to the ERD and the prose. The
Internationalization section at `:192-214` currently offers three future paths; it now records which
one was taken and why. Re-export both SVGs and **apply the known `color-scheme: light dark` fix** —
`docs/index.test.mjs` goes red otherwise.

#### 3. Foundation documents

**Files**: `context/foundation/prd-v2.md`, `context/changes/pl-en-localization/change.md`

**Intent**: Close the two open questions this plan answered and record the one that only production
can answer.

**Contract**: Open Question 1 resolves to "accept the gap", with the widened consequence noted;
Open Question 2 resolves to the 11 confirmed English names; Open Question 3 stays open until the
production walk.

### Success Criteria:

#### Automated Verification:

- Docs structure test passes: `node --test docs/index.test.mjs`
- The full gate is green: `/check`

#### Manual Verification:

- Open `docs/index.html` in a browser and read the changed sections — the Mermaid diagrams cannot be
  rendered headlessly, so a diagram edit is unverified until someone looks at it
- Walk the US-01 path on `2doai.app` in English on a fresh account: sign up, add a dream, request a
  proposal, read it and its first step
- Walk the same path in Polish on the existing account, confirming it was never asked anything and
  still reads Polish
- Confirm the deploy's session loss is expected (sessions are in-memory; a restart discards them)

---

## Testing Strategy

### Unit Tests

- `AppLanguage` resolution: header present, absent, unmatched, malformed, quality-weighted
- The English fallback sentence: month names, elapsed phrasing, singular/plural boundary, passed
  deadline
- `ProposalPrompt` names the right language for each `AppLanguage`
- The Polish fallback is unchanged — `ProposalTemplateTest` must not need editing

### Integration Tests

- Register with each `Accept-Language`, assert the stored language via `/me`
- `PATCH /api/users/me` changes the language and the **same session** reflects it
- `/api/categories` in both languages, plus the `Vary` header
- Model-unavailable fallback in English on the on-demand path
- `proposeScheduled` stays Polish for an English account (the pinned accepted gap)

### Manual Testing Steps

1. Fresh browser set to English → sign up → every surface English, account language `EN`
2. Switch to Polish from the account menu → every surface follows without a reload
3. Reload → Polish persists
4. Existing Polish account → still Polish, never prompted
5. Sign-out → the auth screens follow the browser, and the switch on them works
6. Force a model failure and confirm the fallback sentence is in the account's language

## Performance Considerations

The design's whole performance question is whether it wakes the Neon compute. It does not: `/me`
keeps its zero-query path because the language rides on the principal, and `/api/categories` keeps
its startup-built collections because the per-language map is built once in the constructor. The only
new query is `PATCH /api/users/me`, which a user triggers by hand at most a few times ever. The NFR
of a visible CRUD effect under 500 ms is unaffected — no request gains a round trip.

## Migration Notes

Both migrations are expand-only and additive, so the previous image boots against the new schema and
a rollback is safe. `V10` adds a nullable column whose null means Polish, which is how FR-004 is
satisfied without touching a single existing row. `V11` adds a nullable column and 11 `UPDATE`s
against the seeded reference table; the previous image simply never reads it. Neither migration
rewrites user data. `CategorySyncCheck` is unaffected — it reads only `code`.

## References

- Product framing: `context/foundation/prd-v2.md`, `context/foundation/shape-notes.md`
- Persistence rules and the expand-only precedent: `backend/src/main/resources/db/migration/V9__scheduler_state.sql:19`
- The Neon idleness rule this design is shaped by: `context/foundation/lessons.md`
- The contract-drift rule behind the spec-in-the-same-commit requirement: `context/foundation/lessons.md`
- The locale seams: `category/CategoryController.java:45-78`, `proposal/ProposalPrompt.java:41-50`,
  `frontend/src/layout/AppLayout.tsx:6-16`
- Freeze date and gates: `context/foundation/roadmap.md`

## Progress

> Convention: `- [ ]` pending, `- [x]` done. Append ` — <commit sha>` when a step lands. Do not rename step titles. See `references/progress-format.md`.

### Phase 1: Account language and its transport

#### Automated

- [x] 1.1 Backend suite passes: `cd backend && mvn test` — 130f84d
- [x] 1.2 `ApiSurfaceTest` green — the spec has `PATCH /users/me` — 130f84d
- [x] 1.3 Hibernate `ddl-auto=validate` boots green against V10 — 130f84d
- [x] 1.4 `AuthApiTest`: registration inherits `Accept-Language`; null column reads `PL`; `PATCH` is visible on the same session — 130f84d
- [x] 1.5 `UserRepositoryTest` covers the targeted language update — 130f84d

#### Manual

- [x] 1.6 Register from an English browser and confirm `/me` reports `EN` — 130f84d
- [x] 1.7 Switch via `PATCH`, reload, confirm it survives — 130f84d

### Phase 2: Category names in two languages

#### Automated

- [x] 2.1 Backend suite passes: `cd backend && mvn test` — 3d87762
- [x] 2.2 `CategoryApiTest` returns English, Polish and the English default per header — 3d87762
- [x] 2.3 `CategoryApiTest` asserts `Vary: Accept-Language` — 3d87762
- [x] 2.4 `CategorySeedTest` asserts 11 non-blank `name_en` — 3d87762

#### Manual

- [ ] 2.5 `curl` the endpoint with each header and read the 11 names
- [ ] 2.6 Confirm no cached Polish response is served after switching

### Phase 3: Generated proposal text follows the account

#### Automated

- [x] 3.1 Backend suite passes: `cd backend && mvn test` — 2a65cc8
- [x] 3.2 `ProposalPromptTest` asserts the language word per `AppLanguage` — 2a65cc8
- [x] 3.3 English fallback test class covers sentence, elapsed phrasing, plural boundary, passed deadline — 2a65cc8
- [x] 3.4 `ProposalApiTest`: English request + failed model → English `TEMPLATE` sentence — 2a65cc8
- [x] 3.5 A test pins `proposeScheduled` to Polish (the accepted gap) — 2a65cc8

#### Manual

- [ ] 3.6 Request an English proposal against the real model and read it (PRD Open Question 3)
- [ ] 3.7 Confirm a Polish entry quoted inside English prose reads as intended

### Phase 4: Frontend i18n and catalog extraction

#### Automated

- [x] 4.1 Type check and build pass: `cd frontend && npm run build`
- [x] 4.2 Lint passes including the non-ASCII rule: `cd frontend && npm run lint`
- [x] 4.3 Removing an `en` key fails the build (verified once, then restored)

#### Manual

- [ ] 4.4 Auth-screen switch changes both screens without a reload
- [ ] 4.5 Account-menu switch follows every surface and survives a reload
- [ ] 4.6 `<html lang>` follows the switch and the date input's format follows it

### Phase 5: Test migration to English

#### Automated

- [ ] 5.1 Frontend suite passes: `cd frontend && npm test`
- [ ] 5.2 Lint and build still pass
- [ ] 5.3 The full gate is green: `/check`

#### Manual

- [ ] 5.4 Walk the US-01 path in English, then in Polish
- [ ] 5.5 Confirm no screen shows a raw i18next key

### Phase 6: Living documentation and production walk

#### Automated

- [ ] 6.1 Docs structure test passes: `node --test docs/index.test.mjs`
- [ ] 6.2 The full gate is green: `/check`

#### Manual

- [ ] 6.3 Read the changed `docs/index.html` sections in a browser, diagrams included
- [ ] 6.4 Walk US-01 on `2doai.app` in English on a fresh account
- [ ] 6.5 Walk the same path in Polish on the existing account
- [ ] 6.6 Confirm the expected post-deploy session loss
