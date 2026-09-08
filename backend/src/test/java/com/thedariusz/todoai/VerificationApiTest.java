package com.thedariusz.todoai;

import java.io.IOException;
import java.util.Locale;
import java.util.Map;

import com.thedariusz.todoai.auth.EmailVerificationService;
import com.thedariusz.todoai.auth.VerificationThrottle;
import io.restassured.response.ValidatableResponse;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * End-to-end HTTP tests of address verification (DEV-51): the code that arrives with a sign-up, the
 * two public resources that spend and re-issue it, and the login gate that makes the whole thing
 * mean something. Driven with REST Assured against a real server for the same reason
 * {@code AuthApiTest} is — CSRF, the session cookie and the authentication provider's own ordering
 * are all in the path, and the last of those is what several of these cases are actually about.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class VerificationApiTest extends ApiTestBase {

	private static final String PASSWORD = "correct-horse";

	/** Only to age a code past its expiry; nothing in the application can move that column back. */
	@Autowired
	JdbcTemplate jdbc;

	/**
	 * Used for exactly one thing — dropping the per-address cooldown where a case is about something
	 * else. Signing up twice for the same address inside a minute is throttled by design, and that
	 * rule has its own case below and its own unit test.
	 */
	@Autowired
	VerificationThrottle throttle;

	@Test
	void mailsACodeWhenAnAccountIsRegistered() {
		String email = uniqueEmail();

		signUp(email, PASSWORD).statusCode(201);

		assertThat(mail.codeFor(email)).as("registration mails a six-digit code").isPresent();
	}

	/**
	 * The whole point of the slice: an account nobody has confirmed cannot hold a session, and the
	 * {@code type} is what lets the SPA send the user to the code screen rather than telling them
	 * their password is wrong.
	 */
	@Test
	void refusesToLogInAnUnverifiedAccountAndSaysWhy() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);

		login(email, PASSWORD)
				.statusCode(403)
				.contentType("application/problem+json")
				.body("type", equalTo("urn:2doai:problem:email-not-verified"))
				.body("title", equalTo("Email not verified"));
	}

	/**
	 * And the other half, which is the security-load-bearing one: a <em>wrong</em> password on an
	 * unverified account is the same generic 401 as anywhere else. The default provider checks the
	 * account status <em>before</em> the password, which would answer "not verified" to any string at
	 * all and turn the login form into an oracle for which addresses have unproved accounts —
	 * {@code SecurityConfig} moves that check after BCrypt precisely so this case holds.
	 */
	@Test
	void keepsAWrongPasswordOnAnUnverifiedAccountAGeneric401() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);

		login(email, "not-the-password")
				.statusCode(401)
				.contentType("application/problem+json")
				.body("title", equalTo("Unauthorized"));
	}

	@Test
	void logsInOnceTheCodeHasBeenTyped() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);

		submitCode(email, mail.codeFor(email).orElseThrow()).statusCode(204);

		login(email, PASSWORD).statusCode(201).body("email", equalTo(email));
	}

	@Test
	void rejectsAWrongCode() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);

		submitCode(email, wrongCodeFor(email))
				.statusCode(403)
				.contentType("application/problem+json")
				.body("type", equalTo("urn:2doai:problem:verification-failed"));
	}

	/** An unknown address is refused exactly like a wrong code — the response is not an oracle. */
	@Test
	void rejectsACodeForAnAddressThatHasNoAccount() {
		submitCode(uniqueEmail(), "123456")
				.statusCode(403)
				.body("type", equalTo("urn:2doai:problem:verification-failed"));
	}

	/**
	 * Six digits is a million guesses, which is nothing to a script — so the code is worth five and
	 * the sixth is refused <em>even when it is right</em>. The counter has to survive the 403 it is
	 * charged on, which is the one thing about it that is easy to get wrong: a write that rolls back
	 * with its own response caps nothing.
	 */
	@Test
	void stopsAcceptingTheRightCodeAfterFiveWrongOnes() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);
		String code = mail.codeFor(email).orElseThrow();

		for (int attempt = 0; attempt < 5; attempt++) {
			submitCode(email, wrongCodeFor(email)).statusCode(403);
		}

		submitCode(email, code).statusCode(403);
		login(email, PASSWORD).statusCode(403);
	}

	/**
	 * The other side of the cap, and the one the case above cannot see: four wrong guesses leave the
	 * fifth working. A wrong code is 403 for <em>any</em> cap, so without this a cap of one would keep
	 * the suite green while locking out every user who mistypes once.
	 */
	@Test
	void stillAcceptsTheRightCodeAfterFourWrongOnes() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);
		String code = mail.codeFor(email).orElseThrow();

		for (int attempt = 0; attempt < 4; attempt++) {
			submitCode(email, wrongCodeFor(email)).statusCode(403);
		}

		submitCode(email, code).statusCode(204);
	}

	/** And the way out of it: a fresh code comes with a fresh budget of guesses. */
	@Test
	void aFreshCodeRestoresTheGuessesTheOldOneSpent() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);
		for (int attempt = 0; attempt < 5; attempt++) {
			submitCode(email, wrongCodeFor(email)).statusCode(403);
		}

		throttle.forget(email);
		requestCode(email).statusCode(202);

		submitCode(email, mail.codeFor(email).orElseThrow()).statusCode(204);
	}

	@Test
	void rejectsACodeThatHasExpired() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);
		String code = mail.codeFor(email).orElseThrow();
		jdbc.update("update app_user set verification_expires_at = now() - interval '1 minute' "
				+ "where email = ?", email);

		submitCode(email, code)
				.statusCode(403)
				.body("type", equalTo("urn:2doai:problem:verification-failed"));
	}

	/** A code is spent once: replaying it must not re-verify an account that has since been deleted. */
	@Test
	void refusesToSpendACodeTwice() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);
		String code = mail.codeFor(email).orElseThrow();

		submitCode(email, code).statusCode(204);

		submitCode(email, code).statusCode(403);
	}

	/**
	 * <b>C1.</b> A second sign-up for an address whose owner is at that moment reading the code that
	 * proves it changes nothing at all: not the password, not the code, not the language. An earlier
	 * draft took such an account over — nobody is known to be behind an unproved one, so it looked
	 * free — but the code proves the <em>address</em> and was never bound to the sign-up attempt that
	 * issued it, so the victim's own code would go on to verify the attacker's password onto the
	 * account. One row per address, decided by the UNIQUE index, is what closes it.
	 */
	@Test
	void refusesASecondSignUpForAnAddressNobodyHasVerifiedYet() {
		String email = uniqueEmail();
		signUp(email, "first-password").statusCode(201);
		String code = mail.codeFor(email).orElseThrow();

		signUp(email, "second-password").statusCode(409).contentType("application/problem+json");

		// The code the first sign-up mailed is still the one that works, and it proves the first
		// password onto the account — the second sign-up left no trace of itself anywhere.
		submitCode(email, code).statusCode(204);
		login(email, "second-password").statusCode(401);
		newBrowser();
		login(email, "first-password").statusCode(201);
	}

	/** And the same answer once the address is proved: an account is somebody's, and it stays theirs. */
	@Test
	void refusesASecondSignUpForAVerifiedAddress() {
		String email = uniqueEmail();
		register(email, PASSWORD);

		signUp(email, "second-password").statusCode(409).contentType("application/problem+json");

		login(email, PASSWORD).statusCode(201);
	}

	@Test
	void sendsAnotherCodeOnRequest() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);
		String first = mail.codeFor(email).orElseThrow();

		throttle.forget(email);
		requestCode(email).statusCode(202);

		// The old code is dead the moment a new one is issued — otherwise every resend would widen
		// the window an attacker is guessing into.
		submitCode(email, first).statusCode(403);
		submitCode(email, mail.codeFor(email).orElseThrow()).statusCode(204);
	}

	@Test
	void refusesASecondCodeInsideTheCooldownAndSaysWhenToRetry() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);

		requestCode(email)
				.statusCode(429)
				.contentType("application/problem+json")
				.header("Retry-After", notNullValue())
				.body("title", equalTo("Too Many Requests"));
	}

	/**
	 * 202 for an address with no account, and nothing sent. Answering 404 — or answering 429 only for
	 * addresses that exist — would make this endpoint a way to ask whether somebody has an account
	 * here, which is exactly what registration's 409 is already an accepted, and bounded, leak of.
	 */
	@Test
	void acceptsACodeRequestForAnAddressThatHasNoAccountAndMailsNothing() {
		String stranger = uniqueEmail();

		requestCode(stranger).statusCode(202);

		assertThat(mail.sentAnythingTo(stranger)).isFalse();
	}

	@Test
	void acceptsACodeRequestForAnAlreadyVerifiedAddressAndMailsNothing() {
		String email = uniqueEmail();
		register(email, PASSWORD);
		throttle.forget(email);
		mail.clear();

		requestCode(email).statusCode(202);

		assertThat(mail.sentAnythingTo(email)).isFalse();
	}

	/**
	 * The enumeration defence itself: the throttle is consulted <em>before</em> the address is looked
	 * up, so an address with no account is refused a second code exactly as a real one is. Move
	 * {@code admit} inside the lookup and every other case here stays green while this endpoint becomes
	 * a clean "does this address have an account" oracle — 202 forever for a stranger, 429 for a user.
	 */
	@Test
	void throttlesASecondCodeRequestForAnAddressWithNoAccountJustLikeARealOne() {
		String stranger = uniqueEmail();
		requestCode(stranger).statusCode(202);

		String unknown = requestCode(stranger).statusCode(429).extract().asString();

		String verified = uniqueEmail();
		register(verified, PASSWORD);
		String proved = requestCode(verified).statusCode(429).extract().asString();

		assertThat(unknown)
				.as("the 429 for an address with no account is byte-identical to the one for a real one")
				.isEqualTo(proved);
	}

	/**
	 * Both new endpoints key on the address registration stored, not on the characters typed — the
	 * {@code Email} value object lowercases and strips, and the throttle now does the same, so
	 * capitalizing an address is not a way around either the account lookup or the cooldown.
	 */
	@Test
	void readsAPaddedMixedCaseAddressAsTheSameAccount() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);
		String shouted = "  " + email.toUpperCase(Locale.ROOT) + " ";

		requestCode(shouted).statusCode(429);

		submitCode(shouted, mail.codeFor(email).orElseThrow()).statusCode(204);
		login(email, PASSWORD).statusCode(201);
	}

	/**
	 * The one response with a committed account behind it. The row is created in its own transaction
	 * and the code is mailed after it, so a provider outage is a 503 over an account that exists — and
	 * the way out is "send again", which must work <em>immediately</em>: the send that never happened is
	 * refunded to the throttle rather than charged against the address's hourly budget.
	 */
	@Test
	void commitsTheAccountWhenTheProviderRefusesTheCodeAndLetsTheUserAskAgain() {
		String email = uniqueEmail();
		mail.failNextSend();

		signUp(email, PASSWORD).statusCode(503).contentType("application/problem+json");

		// No throttle.forget here: the refund is what makes this 202 rather than 429.
		requestCode(email).statusCode(202);
		submitCode(email, mail.codeFor(email).orElseThrow()).statusCode(204);
		login(email, PASSWORD).statusCode(201);

		newBrowser();
		signUp(email, PASSWORD).statusCode(409);
	}

	/**
	 * FR-019 — erasing the account erases what the throttle remembers about its address, through the
	 * same {@code PerUserDataDeleter} seam as every other per-user record. Without it the very next
	 * person to type that address waits out a cooldown earned by somebody who no longer exists: the
	 * request below would answer 429 instead of 202, and no {@code throttle.forget} is helping it.
	 */
	@Test
	void forgetsTheThrottlesEntryWhenTheAccountIsDeleted() {
		String email = uniqueEmail();
		register(email, PASSWORD);
		login(email, PASSWORD).statusCode(201);
		csrfAware()
				.body(Map.of("password", PASSWORD))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(204);

		newBrowser();
		requestCode(email).statusCode(202);

		// And the address is registerable again, code and all — a fresh one, which works.
		signUp(email, PASSWORD).statusCode(201);
		submitCode(email, mail.codeFor(email).orElseThrow()).statusCode(204);
	}

	@Test
	void rejectsACodeThatIsNotSixDigits() {
		submitCode(uniqueEmail(), "12345")
				.statusCode(422)
				.contentType("application/problem+json");
	}

	@Test
	void rejectsAVerificationCarryingNoCsrfToken() {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);

		anonymous()
				.body(Map.of("email", email, "code", mail.codeFor(email).orElseThrow()))
				.when()
				.post("/api/verifications")
				.then()
				.statusCode(403);

		// And nothing was spent: the code still works through the proper path.
		submitCode(email, mail.codeFor(email).orElseThrow()).statusCode(204);
	}

	/**
	 * The guard that spans the backend/frontend boundary (lessons.md), for the literals this slice
	 * adds — the same shape as {@code AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode}.
	 * The two URNs are taken off a <em>real</em> response and held against {@code openapi.yaml} and
	 * against the one TypeScript file that branches on them, so a rename anywhere goes red. Asserting
	 * each side against its own copy would leave both suites green while the two disagree.
	 *
	 * <p>{@link EmailVerificationService#CODE_VALIDITY} is the third such literal and the easiest to
	 * miss, because nothing breaks when it drifts: the email interpolates it, while the screen that
	 * says the same thing to the same user spells the number out. Without this the day somebody
	 * shortens the window is the day the SPA starts quietly lying, in both languages.
	 */
	@Test
	void emitsTheVerificationUrnsTheContractAndTheSpaBothHardcode() throws IOException {
		String email = uniqueEmail();
		signUp(email, PASSWORD).statusCode(201);

		String verificationFailed = submitCode(email, wrongCodeFor(email))
				.statusCode(403)
				.extract()
				.path("type");
		String emailNotVerified = login(email, PASSWORD)
				.statusCode(403)
				.extract()
				.path("type");

		assertThat(read(OPENAPI))
				.as("openapi.yaml is the anchor for every wire literal both sides hardcode")
				.contains(verificationFailed)
				.contains(emailNotVerified);
		assertThat(read("../frontend/src/auth/problems.ts"))
				.as("the SPA branches the verification screens on these exact strings")
				.contains(verificationFailed)
				.contains(emailNotVerified);

		String validity = String.valueOf(EmailVerificationService.CODE_VALIDITY.toMinutes());
		for (String catalog : new String[] { "../frontend/src/i18n/pl.ts", "../frontend/src/i18n/en.ts" }) {
			assertThat(read(catalog))
					.as("%s tells the user how long the code lasts, and CODE_VALIDITY decides", catalog)
					// Whole number, not substring: "15 minut" contains "5", so a shortened window would slip
					// straight through the guard that exists to catch exactly that.
					.containsPattern("\\b" + validity + "\\b");
		}
	}

	/**
	 * The slice's other cross-boundary literal, and the one with no runtime symptom at all: how wide a
	 * code is. Four places hold it — the generator, the {@code @Pattern} on
	 * {@code EmailVerificationRequest}, the spec, and the SPA's input, which spends it twice. The first
	 * two now derive from {@link EmailVerificationService#CODE_LENGTH}; these two cannot, so widening
	 * the code would otherwise leave a validator and a form rejecting every code the generator draws,
	 * with nothing red to say so.
	 *
	 * <p>Files only, no request: what is being pinned is a number three files agree on, and the wire
	 * already has {@code rejectsACodeThatIsNotSixDigits} standing over it.
	 */
	@Test
	void usesTheSameCodeWidthTheContractAndTheSpaBothHardcode() throws IOException {
		String digits = "\\d{" + EmailVerificationService.CODE_LENGTH + "}";

		assertThat(read(OPENAPI))
				.as("openapi.yaml is the anchor for the code's shape as much as for the URNs")
				.contains(digits);
		assertThat(read("../frontend/src/pages/VerifyPage.tsx"))
				.as("the SPA constrains its own input to the same width, twice over")
				.contains("pattern=\"" + digits + "\"")
				.contains("maxLength={" + EmailVerificationService.CODE_LENGTH + "}");
	}

	/** Registration without the verification step {@code register} adds — several cases need the gap. */
	private ValidatableResponse signUp(String email, String password) {
		return csrfAware()
				.body(Map.of("email", email, "password", password))
				.when()
				.post("/api/users")
				.then();
	}

	private ValidatableResponse submitCode(String email, String code) {
		return csrfAware()
				.body(Map.of("email", email, "code", code))
				.when()
				.post("/api/verifications")
				.then();
	}

	private ValidatableResponse requestCode(String email) {
		return csrfAware()
				.body(Map.of("email", email))
				.when()
				.post("/api/verification-codes")
				.then();
	}

	/** Six digits that are not the ones mailed — a wrong guess, not a malformed request. */
	private String wrongCodeFor(String email) {
		String issued = mail.codeFor(email).orElse("000000");
		return issued.equals("000000") ? "111111" : "000000";
	}
}
