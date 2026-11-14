# Auth-mail budget after the lookup — Implementation Plan

> **For agentic workers:** REQUIRED SUB-SKILL: Use superpowers:subagent-driven-development (recommended) or superpowers:executing-plans to implement this plan task-by-task. Steps use checkbox (`- [ ]`) syntax for tracking.

**Goal:** Requests for addresses with no account stop spending `AuthMailBudget`; once the budget is spent, a real account's code is dropped silently (202) and the drop is logged.

**Architecture:** `EmailVerificationService.resendCode` keeps the per-address throttle on the request thread and stops calling the budget. `deliverSignInCode`, already running on the auth-mail executor after the lookup, calls `budget.admit` only for an existing account and returns without sending when it says no. The 503 (`AuthMailUnavailableException`) remains for a full executor queue only.

**Tech Stack:** Java 25, Spring Boot 4.1, JUnit 5, REST Assured, `OutputCaptureExtension`; OpenAPI YAML; `docs/index.html`.

**Spec:** `docs/superpowers/specs/2026-11-14-auth-mail-budget-after-lookup-design.md`

## Global Constraints

- `POST /api/verification-codes` answers **202 for every address** when the budget is spent; its 503 means "send queue full" only.
- Logs never carry a code, a link or a full address — only `@domain` (class javadoc of `EmailVerificationService`).
- `event=auth_mail_budget_exhausted` WARN stays once per UTC day (`AuthMailBudget.admit`, unchanged).
- Each dropped code logs one INFO line with structured key `event=auth_mail_code_dropped` and the `@domain`, nothing else identifying.
- A budget-dropped code keeps its throttle entry (not refunded). A provider failure still refunds throttle **and** budget.
- Sign-up (`issue`) stays outside the budget.
- English for code, comments, test names; comments refer to behaviour, not UI copy (`CLAUDE.md`).
- Spec + Java + docs move in one commit (`CLAUDE.md`, Living documentation).

## Review Focus

1. **Unknown-address flood past the budget** — a known account requested afterwards is still mailed. (Task 1, `aFloodOfUnknownAddressesDoesNotSpendTheBudget`.)
2. **Budget spent, known address** — 202, no mail, the drop is logged with the domain and without the address. (Task 1, `dropsAKnownAddresssCodeSilentlyOnceTheBudgetIsSpent`.)
3. **Budget spent, unknown vs known** — identical 202 body/status, so no enumeration. (Task 1, same test, asserts both responses equal.)
4. **Provider failure after admission** — budget slot and throttle entry refunded (existing `givesBackTheThrottleAndTheBudgetWhenTheProviderRefusesACode`, must stay green unchanged).
5. **Dropped code leaves the account's earlier code usable** — the drop happens before `issueVerificationCode`, so a code already in the inbox is not overwritten. (Task 1, assert in test 2: the previously mailed code still signs in.)

---

### Task 1: Budget decided after the lookup, silently

**Files:**
- Modify: `backend/src/main/java/com/thedariusz/todoai/auth/EmailVerificationService.java` (`resendCode` ~L331-365, `deliverSignInCode` ~L456-478, their javadoc)
- Modify: `backend/src/main/java/com/thedariusz/todoai/auth/AuthMailBudget.java` (class javadoc only: "consulted after the lookup, only for an existing account; a spent budget drops silently")
- Modify: `backend/src/main/java/com/thedariusz/todoai/auth/AuthMailUnavailableException.java`, `ApiExceptionHandler.java` (`handleAuthMailUnavailable` javadoc): queue-full only
- Modify: `backend/src/main/java/com/thedariusz/todoai/auth/VerificationController.java` (`sendCode` javadoc: 503 is queue-full only)
- Modify: `backend/src/main/resources/application.properties` (`app.mail.auth-daily-budget` comment: no longer "answer 503", now "codes are dropped and logged")
- Test: `backend/src/test/java/com/thedariusz/todoai/VerificationApiTest.java`

**Interfaces:**
- Consumes: `AuthMailBudget.admit(Instant): boolean`, `AuthMailBudget.refund(Instant)`, `VerificationThrottle.refund(String, Instant)`, test helpers `drainBudget()`, `refill(int)`, `requestCode`, `register`, `mail.sentAnythingTo`, `mail.codeFor`, `mail.clear`, `throttle.forget`, `signInWithCode`/login-by-code helper already used in `provesTheAddressOnTheFirstCodeSignInAndLogsUserRegisteredExactlyOnce`.
- Produces: none (no signature changes; `deliverSignInCode(String email, Instant sentAt)` keeps its signature).

- [ ] **Step 1: Replace `refusesEveryCodeRequestOnceTheDailyBudgetIsSpent` with two failing tests** (add `@ExtendWith(OutputCaptureExtension.class)` on the class, or take `CapturedOutput` as a parameter as `LogPrivacyApiTest` does)

```java
@Test
void dropsAKnownAddresssCodeSilentlyOnceTheBudgetIsSpent(CapturedOutput output) {
	String known = uniqueEmail();
	register(known, PASSWORD);
	String firstCode = mail.codeFor(known).orElseThrow();
	throttle.forget(known);
	mail.clear();
	int drained = drainBudget();
	try {
		String forKnown = requestCode(known).statusCode(202).extract().asString();
		String forStranger = requestCode(uniqueEmail()).statusCode(202).extract().asString();
		assertThat(forStranger).isEqualTo(forKnown);
		assertThat(mail.sentAnythingTo(known)).isFalse();
		assertThat(output.getAll())
				.contains("auth_mail_code_dropped")
				.contains("@" + StringUtils.substringAfter(known, "@"))
				.doesNotContain(known);
	}
	finally {
		refill(drained);
	}
	// Dropped before the code write: the code already in the inbox still works.
	signInWithCode(known, firstCode).statusCode(201);
}

@Test
void aFloodOfUnknownAddressesDoesNotSpendTheBudget() {
	String known = uniqueEmail();
	register(known, PASSWORD);
	throttle.forget(known);
	mail.clear();
	int drained = drainBudget();
	budget.refund(Instant.now()); // exactly one slot left
	try {
		for (int i = 0; i < 5; i++) {
			requestCode(uniqueEmail()).statusCode(202);
		}
		requestCode(known).statusCode(202);
		assertThat(mail.codeFor(known)).isPresent();
	}
	finally {
		refill(drained - 1);
	}
}
```

Use whatever the file already calls the code-sign-in helper (the one `provesTheAddressOnTheFirstCodeSignInAndLogsUserRegisteredExactlyOnce` uses); `uniqueEmail()` addresses share a domain, which is why the test asserts the domain is present and the full address absent.

- [ ] **Step 2: Run them, expect FAIL**

Run: `cd backend && mvn test -Dtest='VerificationApiTest#dropsAKnownAddresssCodeSilentlyOnceTheBudgetIsSpent+aFloodOfUnknownAddressesDoesNotSpendTheBudget'`
Expected: both FAIL — first on `statusCode(202)` (got 503), second on `mail.codeFor(known)` empty (the unknown addresses spent the slot).

- [ ] **Step 3: Move the budget into `deliverSignInCode`**

In `resendCode`: delete the `budget.admit` block; on `RejectedExecutionException` refund **only the throttle** (`throttle.refund(email, sentAt)`), since nothing was taken from the budget yet. Update its javadoc (`@throws AuthMailUnavailableException` when the queue is full).

In `deliverSignInCode`'s account branch, before `send(...)`: if `!budget.admit(sentAt)`, log

```java
log.atInfo()
	.addKeyValue("event", "auth_mail_code_dropped")
	.log("Dropped a code for a @{} address: the daily auth-mail budget is spent", domainOf(email));
```

and return. The no-account branch does not touch the budget. Failure handling: `giveBack` (throttle + budget) applies only after the budget admitted the send; a failure *before* admission (the lookup itself throwing) refunds the throttle only. Simplest shape: track `boolean admitted` in the task and have the catch blocks call `budget.refund(sentAt)` only when it is true.

- [ ] **Step 4: Run the new tests and the existing budget/throttle tests, expect PASS**

Run: `cd backend && mvn test -Dtest='VerificationApiTest,AuthMailBudgetTest,VerificationThrottleTest'`
Expected: PASS, including the unchanged `givesBackTheThrottleAndTheBudgetWhenTheProviderRefusesACode`.

- [ ] **Step 5: Update the javadoc/comment files listed above**, then `cd backend && mvn -q compile` — exit 0.

- [ ] **Step 6: No commit yet** — the 503's meaning is a wire change, so Java, spec and SPA land in one commit at the end of Task 2 (`CLAUDE.md`).

### Task 2: Contract, SPA comment and living documentation

**Files:**
- Modify: `context/foundation/openapi.yaml` (~L39 tag comment, ~L407-413 description, ~L439-444 503 description: "the send queue is full" only; describe the silent drop under the 202)
- Modify: `frontend/src/pages/AuthPage.tsx` (the `status === 503` comment in `messageFor`: queue full, not budget)
- Modify: `docs/index.html` — walk the nine surfaces in `CLAUDE.md`; known hits: L335-338 (overview change note), L606 (decisions table row: budget is consulted after the lookup and only by real accounts; the remaining risk is someone who knows real addresses), L879-886 (flow prose: "an exhausted budget drops the code and logs `auth_mail_code_dropped`; a full queue is the only 503"), L956 and L971-973 (class diagram: `resendCode` text, `admit` comment), and the `Verified against …` stamp at the end of `#overview`
- Modify: `docs/superpowers/specs/2026-11-14-auth-mail-budget-after-lookup-design.md` — none expected; touch only if the build diverged

**Interfaces:**
- Consumes: Task 1's behaviour and the log key `auth_mail_code_dropped`.
- Produces: nothing code depends on.

- [ ] **Step 1: Edit the three files as listed.** No mermaid syntax changes beyond text inside existing lines.

- [ ] **Step 2: Run the full gate**

Run: `/check` (backend tests + frontend test + lint + build), then `node --test docs/index.test.mjs`
Expected: all green.

- [ ] **Step 3: grep for stale claims**

Run: `grep -rn -i "budget.*503\|503.*budget" context/foundation/openapi.yaml docs/index.html backend/src frontend/src`
Expected: no line still says a spent budget answers 503.

- [ ] **Step 4: Commit** (only when the user asks)

```bash
git add backend/src context/foundation/openapi.yaml frontend/src/pages/AuthPage.tsx docs/index.html
git commit -m "fix(auth): decide the auth-mail budget after the lookup, dropping silently"
```
