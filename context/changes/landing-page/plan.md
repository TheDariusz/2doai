# Public landing page implementation plan

## Overview

Explain 2do AI to prospective users before asking them to register. Add a public, bilingual landing page with a responsive fictional product preview, retaining the existing account flows and signed-in entry experience.

Complexity: medium frontend scope, low architectural risk. Routing, localization, visual isolation, and browser verification interact; no backend or schema changes are needed. The conversation and change brief settle the audience, route structure, and primary visual reference. The user explicitly selected a responsive illustrative preview during planning.

## Current State Analysis

- `frontend/src/App.tsx:22` puts the index route inside `ProtectedRoute`; anonymous visitors therefore see login. The wildcard redirects to `/` (`:32`).
- `frontend/src/auth/AuthProvider.tsx` already supplies loading/authenticated/anonymous status and performs the session bootstrap. Failed bootstrap resolves as anonymous; preserve that behavior.
- `frontend/src/App.test.tsx` covers deep-link return, unknown paths, auth form state isolation, and authenticated root navigation. Update the anonymous unknown-path expectation rather than dropping it.
- `frontend/src/pages/AuthPage.tsx:82` and `frontend/src/i18n/LanguageSwitch.tsx:19` provide the pre-login language-switch pattern. Existing detection and persistence live in `frontend/src/i18n/index.ts:42`; English is typed against Polish in `frontend/src/i18n/en.ts:11`.
- `frontend/src/index.css:41` changes global tokens for dark mode. Landing styling must be scoped so its selected light direction does not alter the application shell.
- `frontend/src/pages/ProposalCard.tsx:111` loads private proposals on mount. It must not be mounted as a public demo.
- `frontend/public/manifest.webmanifest:4` launches `/`. Keep it unchanged; authenticated visitors still reach `/goals`.
- The frontend is a Vite SPA on Cloudflare Pages. This change needs no new framework, package, service, API contract, or data model.

## Desired End State

| Entry/state | Result |
| --- | --- |
| `/`, anonymous | Public landing with product explanation and signup/login links |
| `/`, session loading | Render public static content immediately; never render private content |
| `/`, authenticated | Replace navigation to `/goals` |
| Unknown path | Existing redirect to `/`, then the appropriate public/authenticated result |
| Protected deep link, anonymous | Existing login redirect with full return location preserved |
| `/login`, `/register`, `/verify` | Existing standalone flows continue to work |

Rendering public content while the session check is pending prevents backend latency from hiding the service description. A signed-in visitor may briefly see it before redirect; accept that small tradeoff rather than adding a new session mechanism. Landing content issues no data requests; the existing provider bootstrap remains the sole permitted request attributable to a public visit.

## Scope and Content Contract

1. Header: existing 2do AI identity, shared language switch, and Sign in link.
2. Split hero: benefit-led headline, short explanation, Create account link to `/register`, and See how it works anchor to the explanation section.
3. Preview: a labeled fictional example showing a current task, long-term goal, someday dream, and a suggestion referring to that dream. Present response choices as explanatory text, not fake actionable buttons. It is readable HTML, not an image or live authenticated component.
4. How it works: capture what matters, revisit neglected goals, and take a first step. Explain that users can start, defer, or stop reminders about a particular entry.
5. Concise FAQ: account/email verification, how suggestions arrive in the app and by email, user control over a suggested entry, and account/data deletion. Verify each claim against current behavior; omit unsupported guarantees.
6. Closing Create account link. No pricing, testimonials, invented adoption counts, calendar functionality, or anonymous interactive demo.

All visitor-facing text, preview content, accessible labels, and the Example caption belong in PL/EN catalogs. Reuse existing auth action labels where their meaning matches. Suggested hero copy: EN “Your everyday tasks. Your someday dreams.” / PL “Codzienne zadania. Marzenia na kiedyś.” Supporting copy must describe assistance, not promise guaranteed outcomes.

Do not claim full offline functionality, universal email localization, configurable notification frequency, or absolute third-party privacy guarantees. Scheduled proposal email language has an existing exception documented in `docs/index.html:220`.

## Visual Reference Lock and Decision Ledger

Primary reference: [Todoist on Refero](https://styles.refero.design/style/729ba7a8-35d5-44f3-abc0-1078ff6a3467), selected in the preceding conversation. Secondary authority: the existing 2do AI shell and proposal presentation. This is an adaptation of the selected direction, not a Todoist clone or a new brand exercise.

| Decision | Source and role | Commitment |
| --- | --- | --- |
| Composition | Selected Refero screenshot | Benefit text left, substantial readable product preview right; stack on mobile |
| Canvas and text | Refero Paper/Ink tokens | Landing-only `#fefdfc` canvas and `#25221e` primary text; muted text `#6f6c69` |
| Preview surroundings | Refero Cream role | `#fff6f0` decorative preview backing only; avoid tinting every section |
| Brand and type | Existing `index.css` | Keep Bricolage headings and system body; preserve existing purple brand for functional CTA emphasis |
| Shape and rhythm | Refero spacing/shape | Around 1200px max width, 48–80px section gaps, 8px controls, subtle borders; no heavy card stacks |
| Media | User choice | Static responsive localized HTML with fictional data; no external imagery, accounts, or model calls |
| Distinctive detail | 2do AI product behavior | Visually connect the fictional dream to the AI suggestion that brings it back into focus |

Scope variables and selectors beneath a landing wrapper, including `color-scheme: light`, so OS dark mode cannot mix dark controls into a light page. Do not replace root tokens. Style CTA anchors as anchors rather than changing semantics to obtain `button.primary` styling. Keep visible focus indicators, readable contrast, and generous touch targets. Check primary CTA contrast using the existing brand color; adjust only a landing-specific action token if necessary.

Reject decorative mixed-font headline words, Todoist's logo/colors, calendar mockups, giant gradients, new animation libraries, and unrelated feature grids. Preserve the preview's prominence; do not reduce it to decorative empty boxes.

## Phase 1: Public entry and localized content

### Changes Required

- **Files:** `frontend/src/App.tsx`, `frontend/src/App.test.tsx`.
  **Intent:** Make the service explanation accessible before authentication while preserving private route protection.
  **Contract:** Public `/` follows the state table above; protected routes and auth route keys remain intact. Extend route tests first, observe the relevant failures, then wire the smallest route change.
- **Files:** new `frontend/src/pages/LandingPage.tsx` and `LandingPage.test.tsx`.
  **Intent:** Deliver the content contract and fictional preview without coupling public rendering to private data.
  **Contract:** Semantic header/main/sections, one h1, functioning navigation links, labeled static preview, and no API calls or embedded GoalsPage/ProposalCard/AppLayout. Use native details/summary for FAQ if disclosure is needed.
- **Files:** `frontend/src/i18n/pl.ts`, `frontend/src/i18n/en.ts`.
  **Intent:** Localize the complete visitor experience using the existing catalogs and language switch.
  **Contract:** Matching landing keys; local pre-login selection persists through navigation to login/register without a user-settings write.
- **Files:** `frontend/src/auth/AuthProvider.test.tsx` or route integration tests, only as needed for uncovered behavior.
  **Intent:** Prove public rendering survives pending/failed bootstrap and cannot load private data.
  **Contract:** Test a pending session, transition to authenticated, anonymous 401, and non-401 bootstrap failure. Permit the existing `/api/users/me` request; reject calls to categories/goals/proposals from the anonymous landing. Reuse provider behavior rather than refactoring it.

### Success Criteria

#### Automated Verification

- Focused frontend tests pass for public entry, auth transitions, wildcard behavior, language choice, and CTA destinations after a recorded red/green cycle.
- Existing deep-link and login/register/verification regression tests pass.
- Frontend lint and build pass with matching PL/EN catalog keys.

#### Manual Verification

- Read the English and Polish copy and fictional preview; every product claim is supported and all navigation targets behave as described.

## Phase 2: Visual finish, documentation, and release checks

### Changes Required

- **Files:** `frontend/src/index.css`, `frontend/src/pages/LandingPage.tsx`.
  **Intent:** Implement the locked composition and mobile layout while retaining the current app appearance.
  **Contract:** Landing-scoped styling, mobile stacked preview, no horizontal scrolling at 360px, visible keyboard focus, no false interactive controls. Use balanced headings and avoid unnecessary motion. Inspect both languages at 360px, 390px, 768px, and desktop width; repeat with OS dark preference.
- **Files:** `frontend/index.html`.
  **Intent:** Add a concise, accurate static product meta description alongside the existing title.
  **Contract:** One site-level English description matching the default HTML language. Preserve icons, manifest, and existing font resources. No SSR, prerendering, social-image generation, or SEO package in this slice.
- **Files:** `docs/index.html`; auth/language comments in touched source where now inaccurate.
  **Intent:** Describe the public entry, shared language switch, and unchanged protected app flow.
  **Contract:** Walk all nine living-documentation surfaces from CLAUDE.md, updating changed facts including frontend overview/code map/flows and the verification note. Record the actual change/date without inventing an issue ID. Review AuthPage's comment claiming its screens are the only pre-account screens. Do not alter OpenAPI or ER diagrams when their contracts did not change.

### Success Criteria

#### Automated Verification

- Full `/check` passes: backend tests, frontend tests, frontend lint, and frontend build.
- `node --test docs/index.test.mjs` and `git diff --check` pass.

#### Manual Verification

- Desktop and mobile renders match the reference lock in both languages, including OS dark preference, 200% zoom, and keyboard navigation.
- Sign in, registration, verification, protected deep-link return, and signed-in root entry still work; public landing remains readable when the API is unavailable.
- All nine documentation surfaces are reviewed against the final implementation, and rendered screenshots are retained for review.

## Testing Strategy

Use existing Vitest/Testing Library and MemoryRouter helpers. Assert behavior and accessible roles, not CSS class snapshots or exact prose everywhere. Cover initial loading then authenticated transition with an actual provider integration or controlled context rerender. Clear or restore language/storage state between tests. New nontrivial behavior starts with a failing test.

Run focused tests during phase 1 from `frontend/`, for example `npx vitest run src/App.test.tsx src/pages/LandingPage.test.tsx src/pages/AuthPage.test.tsx src/pages/VerifyPage.test.tsx src/auth/ProtectedRoute.test.tsx src/auth/AuthProvider.test.tsx`, then `npm run lint` and `npm run build`. Final `/check` runs all four required gates; do not report stored results as fresh runs. Browser QA verifies appearance and interactions that jsdom cannot establish.

## Performance, Migration, and Rollback

No dependencies, migrations, personal data fixtures, or new network calls. Keep the illustrative preview small and server-independent. Existing JavaScript delivery and session-bootstrap timing remain; this slice does not promise no-JavaScript rendering or improved search indexing. The installed-app start URL stays `/`. A frontend revert restores the old root redirect without affecting accounts or persisted data.

## Risks and Boundaries

- An authenticated visitor may briefly see public content during bootstrap; this is an explicit availability tradeoff, not a private-data flash.
- The existing bootstrap treats failures as anonymous. Do not broaden this change into auth recovery work.
- Light landing versus dark app is deliberate and must be visually checked at navigation boundaries.
- Illustrative content must remain recognizable as an example and consistent with real capabilities.
- Planning creates no deployment or merge authorization; follow normal feature-branch and PR conventions during implementation.

## References

- `context/changes/landing-page/change.md`
- Selected Todoist Refero link above; visual details inspected in this conversation.
- `CLAUDE.md`, `context/foundation/lessons.md`, `context/foundation/tech-stack.md`, `context/foundation/data-model.md`
- `frontend/src/App.tsx:22`, `frontend/src/auth/ProtectedRoute.tsx:5`
- `frontend/src/i18n/LanguageSwitch.tsx:19`, `frontend/src/i18n/index.ts:42`
- `frontend/src/pages/ProposalCard.tsx:111`, `frontend/src/index.css:41`
- `docs/index.html:226`, `docs/index.html:482`, `.agents/skills/check/SKILL.md`

## Progress

### Phase 1: Public entry and localized content

#### Automated

- [x] 1.1 Focused frontend tests pass for public entry, auth transitions, wildcard behavior, language choice, and CTA destinations after a recorded red/green cycle.
- [x] 1.2 Existing deep-link and login/register/verification regression tests pass.
- [x] 1.3 Frontend lint and build pass with matching PL/EN catalog keys.

#### Manual

- [x] 1.4 Read the English and Polish copy and fictional preview; every product claim is supported and all navigation targets behave as described.

### Phase 2: Visual finish, documentation, and release checks

#### Automated

- [ ] 2.1 Full `/check` passes: backend tests, frontend tests, frontend lint, and frontend build.
- [ ] 2.2 `node --test docs/index.test.mjs` and `git diff --check` pass.

#### Manual

- [ ] 2.3 Desktop and mobile renders match the reference lock in both languages, including OS dark preference, 200% zoom, and keyboard navigation.
- [ ] 2.4 Sign in, registration, verification, protected deep-link return, and signed-in root entry still work; public landing remains readable when the API is unavailable.
- [ ] 2.5 All nine documentation surfaces are reviewed against the final implementation, and rendered screenshots are retained for review.
