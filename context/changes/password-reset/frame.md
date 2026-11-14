# Frame Brief: Account recovery vs. replacing the sign-in credential

> Framing step before /10x-plan. This document captures what is *actually*
> at issue, separated from what was initially assumed.

## Reported Observation

A registered user who forgets their password has no way back into the account.
There is no password reset or change feature. The owner is in this state now
and cannot sign in to production (`change.md`, 2026-11-14).

## Initial Framing (preserved)

- **User's stated cause or approach**: the password itself is the wrong
  credential, not merely the missing reset.
- **User's proposed direction**: skip building a reset; switch sign-in straight
  away to a magic link together with a code.
- **Pre-dispatch narrowing**:
  - Leading observation: "no way back" for any registered user; the owner's own
    lockout is the first instance, not the whole problem.
  - "Together with a code": one e-mail carrying both a clickable link and a
    typeable code.
  - Password after the change: gone entirely (sign-up, sign-in, and the
    password confirmation on account deletion).

## Dimension Map

1. **Recovery gap** — only a recovery path is missing; the password credential
   is otherwise fine.
2. **Credential model** — the password is the wrong credential for this app and
   should not exist.  ← initial framing
3. **Sign-in frequency and device context** — how often a sign-in happens and
   where it lands decides what any e-mail-based credential costs the user.
4. **E-mail as a single channel** — whatever depends on a mail arriving inherits
   its deliverability and quota, with no second channel.
5. **Recorded decisions** — the gap exists because recovery and magic link were
   deferred on a premise that may no longer hold.

## Hypothesis Investigation

| Hypothesis | Evidence | Verdict |
| --- | --- | --- |
| 1. Recovery gap only | A hardened e-mailed code exists (6 digits, 15 min, 5 attempts, throttle, timing guard: `EmailVerificationService.java:49,57,262`, `VerificationThrottle.java:50-57`). But it is not reusable as-is: every write is gated on `emailVerifiedAt is null` (`UserRepository.java:108,131,156`, `User.java:222-225`), one code slot, no purpose column, and `User` has no way to change `password_hash`. A reset is new write paths, not a small add-on. | WEAK |
| 2. Credential model (initial framing) | Code-side friction is real: custom `@Utf8ByteLength` for BCrypt's 72 bytes (`Utf8ByteLength.java:20-48`), a dedicated re-auth exception and URN (`ApiExceptionHandler.java:121-135`), hash carried in every session principal (`UserPrincipal.java:33`), customised provider ordering (`SecurityConfig.java:217-226`), no brute-force protection on login. User-side friction is not observed: the owner has not noticed frequent sign-ins. Removal is moderate, concentrated in auth/security: `password_hash NOT NULL` (`V4:17`), `SessionController.java:73-87`, three request records, openapi schemas, ~15 password-specific `AuthApiTest` cases; most other tests go through `ApiTestBase.java:117-175`. | PARTIAL, leaning STRONG |
| 3. Sign-in frequency and device | 30-minute idle timeout, browser-session cookie, no remember-me, in-memory sessions dropped on every backend deploy (`application.properties:37-46`, `auth-session-model.md:48`, `backend.yml`). Any 401 bounces to `/login` silently (`client.ts:75-80`, `ProtectedRoute.tsx:9-15`). App installs standalone (`manifest.webmanifest:4-5`); no route accepts a token in the URL; cookie is `SameSite=Strict`. No service worker yet. | STRONG as a coupled constraint |
| 4. E-mail as single channel | `outlook.com` silently drops mail from the young domain (`lessons.md:40-44`, `deployment-runbook.md:413-420`). Resend free plan is 100/day, shared with proposals, no global cap (`deployment-runbook.md:349,385`). Sending is synchronous, up to ~30 s, 503 on failure, no retry (`EmailSender.java:20-28`). No document assesses e-mail as a sign-in channel. | STRONG as a coupled constraint |
| 5. Recorded decisions | Magic link deferred because "magic link wymagałby infrastruktury e-mail już w S-01" (`prd.md:81`); reset excluded for the same reason (`account-and-auth/plan.md:101`). That infrastructure shipped 2026-09-01 and 2026-09-08. `docs/index.html:564` already names password reset as a pending revisit trigger. `prd.md:80` calls the auth method an implementation detail. | STRONG: the deferral premise has expired |

## Narrowing Signals

- **The owner is the only real user with a password account.** There is no
  population to migrate and no one else whose sign-in habit would be broken.
  This removes the main cost that normally argues for "reset first, replace
  later".
- **The owner has not noticed the 30-minute / per-deploy sign-out.** The case
  for removing the password rests on the lockout and on code-side friction,
  not on felt sign-in friction. It also means the sign-in frequency is an
  unmeasured quantity that an e-mail credential would multiply.
- **Mailbox providers of future users are unknown.** The DEV-51 lesson
  (`outlook.com` assumed lossy) therefore applies unmitigated.
- **Step 5 inverse check** (done inline, no further agents): if only the
  recovery gap mattered, a reset would be cheap next to DEV-51. It is not;
  both directions need new code paths. If the credential framing were wrong,
  the documents would hold a standing reason for passwords. They hold only the
  expired infrastructure reason. Neither check breaks the initial framing.

## Cross-System Convention

In this project, proof of e-mail ownership is already the gate for an account
existing (DEV-51), and the 409 duplicate-address rule explicitly waits on
"a recovery path" (`EmailAlreadyRegisteredException.java:9-14`,
`docs/index.html:564`). The project's own convention for outbound mail is that
a channel with no second path must be treated as lossy (`lessons.md:40-44`).
The proposed direction matches the first convention and is in tension with the
second.

## Reframed (or Confirmed) Problem Statement

> **The actual problem to plan around is**: ownership of the e-mail address is
> proven once at sign-up and never honoured again, so the account hangs on a
> single remembered secret; closing that means changing the sign-in model, and
> a sign-in model includes session lifetime, re-authentication for account
> deletion, and what happens when the mail does not arrive.

The initial framing held up: the reason passwords were chosen has expired, a
reset alone would be comparable work, and with one real user there is nothing
to migrate. What changes is the size and name of the thing being planned. It
is not "password reset"; it is a replacement of the credential. Removing the
password does not remove the single point of failure, it moves it from the
user's memory to mail delivery, and today's 30-minute sessions would exercise
that point far more often than a password ever exercised memory.

## Confidence

**MEDIUM** — strong evidence that the deferral premise expired and that the
change is cheap to adopt with one user; weaker on the two coupled constraints,
because sign-in frequency is unobserved and recipients' mail providers are
unknown. No verification step blocks planning, but the plan must take an
explicit position on both rather than inherit today's defaults.

## What Changes for /10x-plan

Plan a sign-in model change, not a recovery feature. The plan has to decide,
as first-class scope: session lifetime, how account deletion (FR-019) is
confirmed without a password, behaviour when the mail is slow, lost or over
quota, and the supersession of `auth-session-model.md` decision 3 and
`prd.md` FR-001 / `:177`. The change-id `password-reset` no longer describes
the work. Separately, the owner's own lockout persists until this ships.

## References

- Source files: `backend/.../session/SessionController.java:73-87`,
  `security/SecurityConfig.java:197-236`, `auth/EmailVerificationService.java`,
  `auth/VerificationThrottle.java`, `user/UserRepository.java:103-158`,
  `user/User.java:48-225`, `db/migration/V4`, `V13`, `V14`,
  `resources/application.properties:37-46,118-136`,
  `frontend/src/pages/AuthPage.tsx`, `VerifyPage.tsx`,
  `auth/AccountMenu.tsx`, `api/client.ts:75-80`,
  `public/manifest.webmanifest`
- Documents: `context/foundation/prd.md:79-81,177`,
  `auth-session-model.md:22,48,55`, `lessons.md:40-44`,
  `deployment-runbook.md:349,385,413-420`, `roadmap.md:35,185,322`,
  `context/changes/account-and-auth/plan.md:101`,
  `context/changes/email-verification/plan.md:91`, `docs/index.html:564`
- Related research: none (`research.md` not present)
- Investigation tasks: three parallel read-only sub-agents (backend password
  and code surface; frontend auth and PWA surface; documents and prior
  decisions)
