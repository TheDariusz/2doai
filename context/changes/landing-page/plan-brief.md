# Public landing page — plan brief

> Full plan: [plan.md](plan.md)

## What & Why

Give prospective users a clear explanation of 2do AI before they create an account. Focus on everyday tasks, long-term goals, and forgotten dreams that AI can help bring back into focus.

## Starting Point

The existing root route is protected and sends visitors to login. Separate login, registration, email verification, PL/EN localization, and the authenticated goals screen already exist.

## Desired End State

Visitors see a bilingual landing page with a benefit-led hero, fictional product preview, a short how-it-works section, and concise FAQs. Create account and Sign in use the existing pages. Signed-in visitors continue directly to their goals.

## Key Decisions Made

| Decision | Choice | Why | Source |
| --- | --- | --- | --- |
| Public entry | `/` for visitors; `/goals` for authenticated users | Explain the service without slowing everyday use | Change brief |
| Authentication | Existing separate pages | Preserve working signup and verification | Change brief |
| Preview | Responsive illustrative HTML with fictional data | Readable and localizable without private API calls | User's planning answer |
| Visual direction | Todoist reference composition with 2do AI brand/type | Combine approachable marketing with product continuity | Selected Refero reference + code research |
| Appearance | Scoped light landing | Preserve the selected reference and existing app dark mode | Plan |
| Session loading | Show public content while session resolves | Keep the explanation available despite API latency | Plan |
| Language | Existing PL/EN catalogs and switch | Preserve current pre-login preference behavior | Code research |

## Scope

In scope: public routing, bilingual copy, fictional preview, signup/login navigation, responsive styling, basic meta description, tests, browser checks, and architecture documentation.

Out of scope: auth redesign, modal login, live anonymous demo, backend/schema changes, new packages, pricing, testimonials, analytics, and SSR/SEO infrastructure.

## Architecture / Approach

Add one landing page under the existing router and auth provider. Render public static content while anonymous or loading; replace navigation to `/goals` once authenticated. Reuse the language switch, catalogs, fonts, and brand identity. Keep the preview separate from components that fetch or mutate account data.

## Phases at a Glance

| Phase | Deliverable | Key risk |
| --- | --- | --- |
| 1. Public entry and localized content | Routes, copy, illustrative preview, behavior tests | Auth/deep-link regression or accidental private reads |
| 2. Visual finish, documentation, and release checks | Responsive reference-matched page and verified handoff | Global style leakage and unsupported claims |

Prerequisites: existing frontend dependencies and normal backend test environment for the final `/check`; no new service credentials. Work on a feature branch when implementation begins.

Estimated effort: small-to-medium frontend change across two phases; browser polish is the main variable.

## Open Risks & Assumptions

- Signed-in visitors may briefly see public content while session bootstrap completes.
- The preview is explicitly labeled as an example and does not provide working task controls.
- Do not promise all emails follow the selected language or advertise unimplemented offline/calendar functionality.
- This remains a JavaScript-rendered SPA; advanced indexing and social previews are outside this change.

## Success Criteria

- Visitors understand the service and reach the existing account flows in either language.
- Signed-in entry and protected deep-link return continue to work.
- Desktop/mobile appearance is verified, and the full quality gate plus documentation checks pass.
