---
id: email-verification
title: "Email verification at sign-up — a 6-digit code proves the address before the account can log in or be emailed"
roadmap_id: hardening (production risk review, 2026-09-08)
status: implementing
created: 2026-09-08
updated: 2026-09-08
prd_refs:
  - FR-001 (założyć konto — email+hasło)
  - FR-011 / FR-018 (natural-rhythm e-mail — must only ever reach an address its owner confirmed)
  - PRD hard guardrail: intimacy / no data leaves to anyone who did not ask for it
---

# Email verification at sign-up

First slice out of the 2026-09-08 production risk review. Registration is open to the
world today with no proof of address: anyone can register `victim@example.com`, write an
advertisement into a task with a past due date, and the natural rhythm mails it from
`2doai.app` within a week. The fix is the standard one — a code sent to the address, and
nothing (no login, no proposal e-mail) until it is entered.

Delivers: `V13` verification columns on `app_user`, a code issued at registration and on
demand, `POST /api/verifications` + `POST /api/verification-codes`, a login that answers
`urn:2doai:problem:email-not-verified` for a confirmed password on an unconfirmed address,
the scheduler wired to `UserVerified` instead of `UserRegistered`, an in-memory per-address
throttle on code e-mails, and a `/verify` screen in the SPA.

Explicitly **not** this change (next issue): per-IP rate limits at Cloudflare and the shared
secret that stops callers bypassing Cloudflare via `2doai.fly.dev`. Verification proves an
address; it does not cap how many addresses one bot owns.

- Plan: `plan.md`
- Brief: `plan-brief.md`
