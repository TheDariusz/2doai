# Passwordless Sign-in — Plan Brief

> Full plan: `context/changes/password-reset/plan.md`
> Frame brief: `context/changes/password-reset/frame.md`

## What & Why

> **The actual problem to plan around is**: ownership of the e-mail address is
> proven once at sign-up and never honoured again, so the account hangs on a
> single remembered secret; closing that means changing the sign-in model, and
> a sign-in model includes session lifetime, re-authentication for account
> deletion, and what happens when the mail does not arrive.

We replace the password with an e-mailed eight-digit code as the only sign-in
credential. The owner is locked out of production today, and the reason
passwords were chosen (no e-mail infrastructure) expired in September.

## Starting Point

Sign-in is e-mail + password with no reset. A hardened e-mailed code already
exists, but only unverified accounts can receive or spend one, and spending it
opens no session. Sessions last 30 idle minutes and are lost on every deploy.

## Desired End State

A visitor types an address and gets one mail with a code and a link that
prefills it; typing the code or pressing the button behind the link signs them
in. They stay signed in across browser restarts and deploys, for up to 90 days
without activity. Account deletion is
confirmed with a fresh code. Nothing in the product mentions a password.

## Key Decisions Made

| Decision | Choice | Why (1 sentence) | Source |
| --- | --- | --- | --- |
| Scope | Replace the credential, not add a reset | A reset is comparable work and there is one real user to migrate | Frame |
| Password afterwards | Gone from sign-up, sign-in and deletion | No standing reason for it remains in the documents | Frame |
| What the link does | Prefills the code; the user presses a button | One secret, and mail scanners cannot spend it on GET | Plan |
| Session lifetime | 30-day in-memory session plus a 90-day DB-backed token read only when the session is missing | A deploy must not cost a mail round trip, and Neon must still sleep | Plan |
| Remember-me token | Does not rotate | Spring's rotation signs every device out, with a 500, when two requests share a cookie | Review |
| Code strength | Eight digits, atomic five-guess cap | Six digits gave a targeted account about a 20% yearly chance of a correct guess | Review |
| Login screen in release 1 | Code only, no password fallback | The one real account cannot use its password; the backend alone keeps rollback safe | Review |
| Deletion confirmation | A fresh code bound to the deletion purpose | A long-lived session on an unattended device must not erase the account | Plan |
| Entry screens | `/register` and `/login` kept, both answer 202 for every address | Removes the 409 existence leak without typos creating accounts | Plan |
| Lost mail | Resend with cooldown and honest copy, no second credential | No new secret to store; long sessions make sign-in rare | Plan |
| Quota guard | In-memory daily budget for auth mail | A sign-up burst must not starve proposal mail on the 100/day plan | Plan |
| Rollout | Two releases, expand then contract | Keeps image rollback safe under the Flyway expand-only rule | Plan |
| Owner's lockout | Sign in by code once release 1 is live | No manual change to production data | Plan |
| Mail timing | Auth mail sent off the request thread | A uniform 202 is meaningless if only known addresses are slow | Plan |
| Link format | Address and code in the URL fragment | Fragments never reach server logs or `Referer` | Plan |

## Scope

**In scope:**
- Code sign-in on `POST /api/sessions`, link in the mail, resend cooldown
- Long sessions and a restart-proof, non-rotating remember-me token
- Uniform 202 on registration and code requests; daily auth-mail budget
- Deletion confirmed by a purpose-bound code
- Removal of every password field, validator, endpoint remnant and copy
- `openapi.yaml` (1.9.0, then 2.0.0), `docs/index.html`, decision records

**Out of scope:**
- Recovery codes, passkeys, OAuth
- Per-IP limits at the Cloudflare edge
- Dropping the `password_hash` column (later change)
- Sessions stored in Postgres, scheduled cleanup jobs, e2e tests

## Architecture / Approach

The existing code machinery is reused rather than duplicated: the
"unverified only" gates are lifted, the single code slot on `app_user` gains a
purpose (`SIGN_IN`, `ACCOUNT_DELETION`), and the first redeemed code also proves
the address. `POST /api/sessions` remains the only place a session is created
and runs the same fixation and CSRF rotation as today. Remember-me uses Spring
Security's persistent token services over a project-owned table. The mail link
points at `/verify#email=…&code=…`, which only prefills the form.

## Phases at a Glance

| Phase | What it delivers | Key risk |
| --- | --- | --- |
| 1. Code sign-in, backend | Verified accounts sign in with a code; link, async mail, budget | Loosening the unverified-only gates without weakening the enumeration and timing guards |
| 2. Code sign-in, frontend | Code-first login, link landing, resend cooldown; owner unlocked | Link opens the browser, not the installed PWA |
| 3. Long sessions | Sign-in that survives deploys and browser restarts | Custom non-rotating remember-me services; the CSRF cookie must be re-issued on auto-login |
| 4. Password removal, backend | Uniform registration, code-confirmed deletion, no password code | Breaking contract (2.0.0) and a wide test rewrite through `ApiTestBase` |
| 5. Password removal, frontend and records | No password UI; all documents superseded | A document that still claims passwords while tests stay green |

**Prerequisites:** a Linear issue and branch for the work (the `docs/index.html`
stamp needs an issue id); an address on a provider that receives `2doai.app`
mail for the owner's account and for the control test.
**Estimated effort:** about 6-8 after-hours sessions across 5 phases, in two
production releases.

## Open Risks & Assumptions

- Frame confidence is MEDIUM: sign-in frequency was never measured and future
  users' mail providers are unknown. Long sessions are the plan's answer to the
  first; the second is accepted.
- A user on a filtering provider (`outlook.com` today) who loses their session
  is locked out until the domain gains reputation. There is no second channel.
- Assumes the owner's address receives `2doai.app` mail. If it does not, the
  account stays locked after release 1 and needs a separate decision.
- The daily budget and the throttle live in memory and reset on restart.
- Accepted: anyone who knows an address can spend its five codes an hour and
  overwrite the owner's pending code, blocking that sign-in while the attack
  runs. A per-IP limit at the edge is the follow-up that closes it.
- A non-rotating remember-me token has no theft detection: a stolen cookie
  works until logout or expiry.
- Logout revokes the user's remember-me tokens on all devices.
- The remember-me and CSRF findings were read from the Spring Security 7.1.2
  and Tomcat 11 sources, not run against a live server.
- Old password hashes stay in the database until the follow-up column drop.

## Success Criteria (Summary)

- The owner signs in to production with a mailed code and finds their data.
- A new user can register, sign in by link or typed code, stay signed in across
  a deploy, and delete their account, without ever choosing a password.
- Registering or signing in with any address gives the same answer, and no
  document or screen still describes a password.
