# Auth-mail budget: decided after the lookup, silently

Short design, PR 70 (DEV-62, passwordless sign-in release 1), review finding 1.

## Intent

`AuthMailBudget` (60 code mails per UTC day) is consulted on the request thread, before the address
is looked up, so a request for an address with no account spends a slot. Any anonymous caller can
spend all 60 with 60 made-up addresses in a minute; every real code request then answers 503 until
midnight UTC. In release 2, with no password, that is a full sign-in lockout anyone can repeat daily.

Goal: unknown addresses cost the budget nothing, without the response revealing which addresses have
accounts and without a database write per request.

## Decided

- **Silent when spent.** `POST /api/verification-codes` answers 202 for every address even when the
  budget is spent; the code is simply not sent. (User's decision.)
- **Traced in the logs** so the operator can see it. (User's decision.) The existing
  `event=auth_mail_budget_exhausted` WARN, once per UTC day, plus one INFO line per dropped code
  carrying only the address's `@domain`.
- **Approach A**: move the budget check into the auth-mail task, after the lookup. No per-account cap.

## Approaches considered

- **A. Budget checked after the lookup** (picked). Smallest change; closes the lockout that needs no
  knowledge. Residual: someone who knows real users' addresses can still drain the budget at 5 codes
  per address per hour.
- **B. A plus a per-account daily cap** (e.g. 6/day). Makes the budget unreachable by code requests
  at today's ≤10 users. Not picked: an extra in-memory counter for a residual the user accepts.
- Dropped: per-IP limit (60 requests a day cannot be told from normal use, and Fly is reachable
  directly); reserved budget share for trusted accounts (more machinery than B for the same result).

## The change

- `EmailVerificationService.resendCode`: throttle on the request thread as today, then hand the task
  to the executor. No budget call here.
- `deliverSignInCode` (executor): look the address up. No account → log as today, spend nothing.
  Account → `budget.admit(sentAt)`; refused → INFO "dropped a code for a @domain address: the daily
  auth-mail budget is spent" and return; admitted → send. A `MailDeliveryException` (or any failure
  after admission) refunds the budget as today; the throttle refund on failure is unchanged.
- **No enumeration oracle**: the response and its timing are the same for every address; the only
  observable is whether mail arrives, which needs the attacker to control a mailbox and to know the
  exact budget count. Accepted.
- `AuthMailUnavailableException` / 503 stays, for a full executor queue only (decided before the
  lookup, uniform for every address). Its javadoc and the `ApiExceptionHandler` javadoc drop the
  budget.

## My calls (correct if wrong)

- **The throttle entry is kept when the budget drops a code.** The request was accepted and answered;
  refunding would only let the same address queue another silently dropped request at once. On a
  provider failure the throttle is still refunded, as today.
- **Sign-up mail stays outside the budget** (review finding 2 is a separate decision).
- Contract and docs move in the same change: `openapi.yaml` (503 on `/verification-codes` is
  queue-full only), the `AuthPage` 503 comment, `application.properties` comment on
  `app.mail.auth-daily-budget`, `AuthMailBudget` javadoc, and `docs/index.html` per the
  Living documentation walk.
- Tests (TDD): an unknown-address flood beyond the budget leaves a known address still mailed; a known
  address past the budget gets 202 and no mail; the existing "budget answers 503" test is rewritten to
  the silent behaviour; a failed send still refunds the budget.

## Left to the builder

Log wording; whether the per-drop INFO uses the structured `addKeyValue("event", …)` form like the
exhaustion line (preferred, so Loki can count drops).
