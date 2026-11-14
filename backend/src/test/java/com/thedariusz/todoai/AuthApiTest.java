package com.thedariusz.todoai;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

import com.thedariusz.todoai.user.AppLanguage;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.Cookie;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.params.provider.Arguments.arguments;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * End-to-end HTTP tests of the account lifecycle — register, log in, read the current user, log
 * out, delete — driven with <b>REST Assured</b> against a real embedded server so the full Spring
 * Security filter chain (CSRF, session cookie, authorization) is genuinely in the path. MockMvc is
 * not used anywhere in this project.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiTest extends ApiTestBase {

	/** Only for reproducing a pre-V10 row; nothing in the application writes a null language. */
	@Autowired
	JdbcTemplate jdbc;

	@Test
	void registersANewUser() {
		String email = uniqueEmail();

		csrfAware()
				.body(Map.of("email", email, "password", "correct-horse"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(201)
				.header("Location", "/api/users/me")
				.body("id", notNullValue())
				.body("email", equalTo(email));
	}

	@Test
	void normalizesEmailBeforeValidatingAndRegistering() {
		String localPart = "User-" + UUID.randomUUID();

		csrfAware()
				.body(Map.of("email", "  " + localPart + "@Example.COM  ", "password", "correct-horse"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(201)
				.body("email", equalTo(localPart.toLowerCase(Locale.ROOT) + "@example.com"));
	}

	@Test
	void rejectsADuplicateEmailWithConflict() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		csrfAware()
				.body(Map.of("email", email, "password", "another-password"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(409)
				.contentType("application/problem+json");
	}

	@Test
	void rejectsAPasswordLongerThanBcryptsUtf8ByteLimit() {
		csrfAware()
				.body(Map.of("email", uniqueEmail(), "password", "😀".repeat(19)))
				.when()
				.post("/api/users")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");
	}

	@Test
	void rejectsAPasswordShorterThanEightCharacters() {
		csrfAware()
				.body(Map.of("email", uniqueEmail(), "password", "short"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");
	}

	@Test
	void logsInAndReadsTheCurrentUser() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		login(email, "correct-horse")
				.statusCode(201)
				.body("email", equalTo(email))
				.body("id", notNullValue());

		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(200)
				.body("email", equalTo(email));
	}

	@Test
	void rejectsBadCredentialsWithAGeneric401() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		login(email, "wrong-password")
				.statusCode(401)
				.contentType("application/problem+json")
				.body("title", equalTo("Unauthorized"))
				.body("status", equalTo(401));
	}

	@Test
	void rejectsAnOverlongLoginPasswordAsUnprocessable() {
		login(uniqueEmail(), "😀".repeat(19))
				.statusCode(422)
				.contentType("application/problem+json");
	}

	@Test
	void logsOutAndInvalidatesTheSession() {
		givenLoggedInUser();

		List<String> setCookies = csrfAware()
				.when()
				.delete("/api/sessions/current")
				.then()
				.statusCode(204)
				.extract()
				.headers()
				.getValues("Set-Cookie");

		assertSessionCookieCancelled(setCookies);

		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(401);
	}

	/**
	 * The CSRF contract is 403 for <em>every</em> caller, authenticated or not — {@code CsrfFilter}
	 * runs before {@code ExceptionTranslationFilter} and so answers through its own access-denied
	 * handler, which never gets the chance to downgrade an anonymous denial to a 401.
	 */
	@Test
	void rejectsAnAuthenticatedMutationCarryingNoCsrfToken() {
		givenLoggedInUser();

		client()
				.when()
				.delete("/api/sessions/current")
				.then()
				.statusCode(403)
				.contentType("application/problem+json")
				.body("title", equalTo("Forbidden"))
				.body("status", equalTo(403))
				// The negative half of the discriminator: a CSRF denial must never look like a
				// mistyped re-auth password. Pinned as *absent* rather than "not the URN" — the
				// latter also passes for any other value, so it could not tell the frontend's
				// fixtures what a real CSRF 403 looks like. Boot 4 omits `type` for an unset
				// ProblemDetail; it does NOT serialize about:blank (verified, not assumed).
				.body("type", nullValue());
	}

	/**
	 * The irreversible operation must not run for a caller who cannot prove same-origin, even with a
	 * valid session and the right password. {@code CsrfFilter} answers before the controller, so this
	 * pins the rejection <em>and</em> that the row survived it.
	 */
	@Test
	void deniesADeletionCarryingNoCsrfTokenAndKeepsTheAccount() {
		String email = givenLoggedInUser();

		client()
				.body(Map.of("password", "correct-horse"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(403)
				.contentType("application/problem+json");

		// And nothing was deleted. Asserted against the database, not the session: GET /users/me
		// answers from the in-memory principal and would still return 200 for an erased account.
		newBrowser();
		csrfAware()
				.body(Map.of("email", email, "password", "correct-horse"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(409);
	}

	@Test
	void deletesTheAccountInvalidatesTheSessionAndFreesTheEmail() {
		String email = givenLoggedInUser();

		List<String> setCookies = csrfAware()
				.body(Map.of("password", "correct-horse"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(204)
				.extract()
				.headers()
				.getValues("Set-Cookie");

		assertSessionCookieCancelled(setCookies);

		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(401);

		// FR-019 is a real erasure, not a soft delete: the address is registerable again — by a
		// fresh visitor, hence a clean client rather than the deleted user's leftover cookies.
		newBrowser();
		register(email, "a-different-password");
	}

	/**
	 * A mistyped confirmation password is not an expired session. Returning the generic 401 here
	 * would have every SPA bounce the user to the login screen, since that is what a 401 on an
	 * authenticated call means everywhere else. There is no enumeration concern — the caller is
	 * already authenticated and already knows the account exists.
	 */
	@Test
	void rejectsDeletionWhenTheReAuthPasswordIsWrong() {
		givenLoggedInUser();

		csrfAware()
				.body(Map.of("password", "not-the-password"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(403)
				.contentType("application/problem+json")
				.body("type", equalTo("urn:2doai:problem:re-auth-failed"))
				.body("title", equalTo("Re-authentication failed"))
				.body("detail", containsString("password"));

		// And the session survives — the user is still logged in, free to retry.
		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(200);
	}

	/**
	 * The one check that spans the backend/frontend boundary (lessons.md). Every side otherwise
	 * asserts against its <em>own</em> copy of the URN, so both suites stay green while the two
	 * disagree — a backend rename that dutifully updates the Java and its tests leaves the SPA
	 * falling through to generic copy, and nothing fails. Here the value the running server emits
	 * is held against the spec <em>and</em> against the TypeScript that branches on it, so renaming
	 * any one of the three goes red. It lives on this side because only this side has a real server;
	 * Vite denies the frontend suite any file above its root.
	 */
	@Test
	void emitsTheReAuthUrnTheContractAndTheSpaBothHardcode() throws IOException {
		givenLoggedInUser();

		String onTheWire = csrfAware()
				.body(Map.of("password", "not-the-password"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(403)
				.extract()
				.path("type");

		assertThat(read(OPENAPI))
				.as("openapi.yaml is the anchor for every wire literal both sides hardcode")
				.contains(onTheWire);
		assertThat(read("../frontend/src/auth/problems.ts"))
				.as("the SPA discriminates the two 403s on this exact string")
				.contains(onTheWire);
	}

	/**
	 * The second cross-boundary guard on this resource, and the one {@code openapi.yaml}'s own comment
	 * promises. {@code AppLanguage} there calls itself "THE ANCHOR for these two literals, the way
	 * GoalLayer is for its three" — and {@code GoalLayer} <em>earns</em> that by being held against the
	 * spec in {@code GoalApiTest.publishesTheWireEnumsTheContractAnchors}. Without the same check the
	 * claim is decoration: each side asserts against its own copy and both stay green while the two
	 * disagree, which is exactly how six of eleven category codes rotted unnoticed (lessons.md, "A
	 * contract value duplicated across the stack needs one guard that spans the boundary").
	 *
	 * <p>The spec is compared as a <b>set</b>, never searched for: a substring check passes happily
	 * after a value has been <em>deleted</em> from the spec. The SPA's single copy — the {@code User}
	 * type in {@code auth-context.ts}, which everything else derives from — is held by substring, the
	 * way the re-auth URN above is: a TypeScript union cannot be read as a set from here, so a value
	 * <em>removed</em> on that side is the one drift this does not see. A rename on either side goes
	 * red.
	 */
	@Test
	void publishesTheLanguageLiteralsTheContractAnchors() throws IOException {
		assertThat(extensibleEnum(openApi(), "AppLanguage"))
				.as("openapi.yaml is the anchor for every language literal the stack hardcodes")
				.containsExactlyInAnyOrderElementsOf(constantNames(AppLanguage.values()));

		String userType = read("../frontend/src/auth/auth-context.ts");
		for (AppLanguage language : AppLanguage.values()) {
			assertThat(userType)
					.as("the SPA's User type names every language the contract does")
					.contains("'" + language.name() + "'");
		}
	}

	/**
	 * FR-019 says the account is erased, so no device may still be holding an authenticated session
	 * for it. {@code logout} only ends the calling request's session, so without expiring the rest a
	 * phone keeps returning 200 for a deleted account until its 30-day idle timeout.
	 */
	@Test
	void endsEverySessionWhenTheAccountIsDeleted() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		login(email, "correct-horse").statusCode(201);
		CookieFilter phone = currentBrowser();

		newBrowser();
		login(email, "correct-horse").statusCode(201);
		csrfAware()
				.body(Map.of("password", "correct-horse"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(204);

		switchToBrowser(phone);
		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(401);
	}

	@Test
	void rejectsAnOverlongLoginEmailAsUnprocessable() {
		login("a".repeat(320) + "@example.com", "correct-horse")
				.statusCode(422)
				.contentType("application/problem+json");
	}

	/**
	 * An anonymous 401 must not cost a server-side session. Spring Security's request cache would
	 * otherwise call {@code request.getSession()} before the entry point runs, so every unauthenticated
	 * hit would pin 30 days of in-memory session on the single Fly machine — an unauthenticated
	 * memory-exhaustion lever. {@code Accept: *&#47;*} is the case that matters: it is what {@code fetch()}
	 * sends, and it passes the request cache's default matcher.
	 */
	@Test
	void doesNotCreateASessionForAnAnonymousRequest() {
		Map<String, String> cookies = anonymous()
				.header("Accept", "*/*")
				.when()
				.get("/api/categories")
				.then()
				.statusCode(401)
				.extract()
				.cookies();

		assertThat(cookies).doesNotContainKey("JSESSIONID");
	}

	/**
	 * A server-side failure must keep its own status. The filter chain runs on the {@code ERROR}
	 * dispatch by default, so without permitting that dispatch the forward to {@code /error} is
	 * re-authorized as an anonymous request and the 500 is rewritten into a misleading 401 — a bug in
	 * our code reported to the user as "your credentials are invalid".
	 */
	@Test
	void doesNotMaskServerErrorsAsUnauthorized() {
		csrfAware()
				.when()
				.post("/api/ping")
				.then()
				.statusCode(500);
	}

	/**
	 * A 500 is the response the SPA is least able to guess at, so it must arrive in the same RFC 9457
	 * shape as every other failure — not Boot's default {@code timestamp/error/path} object. The
	 * detail stays generic: the exception message is ours to read in the logs, not the caller's.
	 */
	@Test
	void rendersServerErrorsAsProblemJson() {
		csrfAware()
				.when()
				.post("/api/ping")
				.then()
				.statusCode(500)
				.contentType("application/problem+json")
				.body("title", equalTo("Internal Server Error"))
				.body("status", equalTo(500))
				.body("detail", notNullValue());
	}

	/** Whatever blew up stays in the logs — no message, no stack frames, no SQL on the wire. */
	@Test
	void neverLeaksExceptionDetailOnAServerError() {
		String body = csrfAware()
				.when()
				.post("/api/ping")
				.then()
				.statusCode(500)
				.extract()
				.asString();

		assertThat(body)
				.doesNotContain("simulated server-side bug")
				.doesNotContain("IllegalStateException")
				.doesNotContain("com.thedariusz");
	}

	/** Anonymous-reachable endpoint that fails the way a real bug would, for the test above. */
	@TestConfiguration
	static class ThrowingEndpoint {

		@RestController
		static class Boom {

			@PostMapping("/api/ping")
			void explode() {
				throw new IllegalStateException("simulated server-side bug");
			}
		}
	}

	/**
	 * Session-fixation protection ({@code ChangeSessionIdAuthenticationStrategy}). Logging in on a
	 * client that already holds a session must issue a <em>different</em> id — drop
	 * {@code sessionAuthenticationStrategy.onAuthentication} from the login flow and no Set-Cookie
	 * arrives at all, so this fails.
	 */
	@Test
	void rotatesTheSessionIdOnLogin() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		String firstSession = login(email, "correct-horse").statusCode(201).extract().cookie("JSESSIONID");
		String secondSession = login(email, "correct-horse").statusCode(201).extract().cookie("JSESSIONID");

		assertThat(firstSession).isNotBlank();
		assertThat(secondSession).isNotBlank().isNotEqualTo(firstSession);
	}

	/**
	 * The rotated CSRF token has to be usable straight from the login response. {@code
	 * CsrfAuthenticationStrategy} only clears the old cookie and defers the replacement, so without
	 * {@code SessionController} materializing it the SPA's first post-login mutation 403s.
	 */
	@Test
	void issuesAUsableCsrfTokenOnTheLoginResponse() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		String rotated = login(email, "correct-horse").statusCode(201).extract().cookie("XSRF-TOKEN");
		assertThat(rotated).isNotBlank();

		// The very next mutation, carrying only what the login response handed back.
		client()
				.header("X-XSRF-TOKEN", rotated)
				.when()
				.delete("/api/sessions/current")
				.then()
				.statusCode(204);
	}

	/** A token minted before login must not survive it — otherwise a planted token stays replayable. */
	@Test
	void retiresThePreLoginCsrfToken() {
		String email = uniqueEmail();
		String preLoginToken = primeCsrfToken();
		register(email, "correct-horse");
		login(email, "correct-horse").statusCode(201);

		client()
				.header("X-XSRF-TOKEN", preLoginToken)
				.when()
				.delete("/api/sessions/current")
				.then()
				.statusCode(403);
	}

	/**
	 * Login is CSRF-protected like any other mutation. Exempting it is the classic "it is public
	 * anyway" mistake: a cross-origin auto-submitting form would log the victim into the
	 * <em>attacker's</em> account, and everything they then write lands in the attacker's data.
	 */
	@Test
	void rejectsALoginCarryingNoCsrfToken() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		Map<String, String> cookies = anonymous()
				.body(Map.of("email", email, "password", "correct-horse"))
				.when()
				.post("/api/sessions")
				.then()
				.statusCode(403)
				.extract()
				.cookies();

		assertThat(cookies).doesNotContainKey("JSESSIONID");
	}

	@Test
	void rejectsARegistrationCarryingNoCsrfToken() {
		String email = uniqueEmail();

		anonymous()
				.body(Map.of("email", email, "password", "correct-horse"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(403);

		// And nothing was written: the address is still free.
		register(email, "correct-horse");
	}

	/**
	 * The session cookie's own attributes are the XSS and CSRF defenses — a property rename in a Boot
	 * upgrade would drop them silently. ({@code Secure} is env-driven and off over plaintext HTTP in
	 * tests, so it is not asserted here.)
	 */
	@Test
	void issuesTheSessionCookieHttpOnlyAndSameSiteStrict() {
		String email = uniqueEmail();
		register(email, "correct-horse");

		Cookie session = login(email, "correct-horse")
				.statusCode(201)
				.extract()
				.detailedCookie("JSESSIONID");

		assertThat(session.isHttpOnly()).isTrue();
		assertThat(session.getSameSite()).isEqualTo("Strict");
	}

	/**
	 * FR-003 — a new account starts in the language its sign-up screen was shown in. There is no field
	 * on the registration payload and there does not need to be: the sign-up screen's language <em>is</em>
	 * the {@code Accept-Language} the sign-up request carried, because the SPA sets that header from
	 * whatever it is currently rendering (including after the switch on the auth screens themselves).
	 *
	 * <p>The cases are a table rather than a test each, because they differ by a header and a letter
	 * and agree on everything else — which is also what makes them readable as the negotiation rule
	 * they collectively state. Two of them carry the whole reason negotiation is left to Spring's
	 * {@code AcceptHeaderLocaleResolver} instead of being hand-parsed; see {@code signUpHeaders}.
	 */
	@ParameterizedTest(name = "Accept-Language {0} registers as {1}")
	@MethodSource("signUpHeaders")
	void registrationInheritsTheLanguageOfTheSignUpRequest(String acceptLanguage, AppLanguage stored) {
		RequestSpecification signUp = csrfAware();
		if (acceptLanguage != null) {
			signUp = signUp.header("Accept-Language", acceptLanguage);
		}

		signUp.body(Map.of("email", uniqueEmail(), "password", "correct-horse"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(201)
				.body("language", equalTo(stored.name()));
	}

	static Stream<Arguments> signUpHeaders() {
		return Stream.of(
				arguments("en-GB,en;q=0.9", AppLanguage.EN),
				arguments("pl-PL,pl;q=0.9,en;q=0.8", AppLanguage.PL),
				// The browser's first choice is one the app does not speak, so the quality-ordered
				// second decides. A naive "read the first tag" parser answers this one wrongly, which
				// is why the negotiation is AcceptHeaderLocaleResolver's rather than ours.
				arguments("fr-FR;q=0.9,pl;q=0.8", AppLanguage.PL),
				// The app is English-first: a header naming neither locale, and no header at all,
				// both mean English.
				arguments("de-DE,de;q=0.9", AppLanguage.EN),
				arguments(null, AppLanguage.EN));
	}

	/** The language is stored, not merely echoed: the next session reads it back off the account. */
	@Test
	void theLanguageChosenAtSignUpIsWhatTheNextLoginAnswersWith() {
		String email = uniqueEmail();

		csrfAware()
				.header("Accept-Language", "pl-PL,pl;q=0.9,en;q=0.8")
				.body(Map.of("email", email, "password", "correct-horse"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(201);
		// Registered by hand rather than through `register`, because the header is the subject here —
		// so the verification step it would have done has to be done explicitly (DEV-51).
		confirmAddress(email);

		login(email, "correct-horse").statusCode(201).body("language", equalTo("PL"));
	}

	/**
	 * FR-004 — an account that existed before this change was never asked anything, so its column is
	 * simply absent (V10 adds it with no {@code DEFAULT} and rewrites no rows). Null means "never
	 * chosen", which reads as Polish, all the way out to the wire. The row is nulled with raw SQL
	 * because nothing in the application can produce that state any more.
	 */
	@Test
	void anAccountThatNeverChoseALanguageReadsAsPolish() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		jdbc.update("update app_user set preferred_language = null where email = ?", email);

		newBrowser();
		login(email, "correct-horse").statusCode(201).body("language", equalTo("PL"));

		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(200)
				.body("language", equalTo("PL"));
	}

	/**
	 * FR-002, and the one lifecycle trap in this slice. The PATCH writes the column, but the session
	 * still holds the {@code UserPrincipal} built at login — and {@code GET /api/users/me} is answered
	 * from that principal with no query. Unless the handler replaces the {@code Authentication} in the
	 * {@code SecurityContext} <em>and</em> persists the context, the user switches language and
	 * {@code /me} keeps reporting the old one until they log out.
	 */
	@Test
	void switchingTheLanguageIsVisibleOnTheSameSessionWithoutRelogin() {
		givenLoggedInUser();

		csrfAware()
				.body(Map.of("language", "PL"))
				.when()
				.patch("/api/users/me")
				.then()
				.statusCode(200)
				.body("language", equalTo("PL"));

		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(200)
				.body("language", equalTo("PL"));
	}

	/** And the other half: the choice is on the account, so it outlives the session that made it. */
	@Test
	void theSwitchedLanguageSurvivesALaterLogin() {
		String email = givenLoggedInUser();

		csrfAware()
				.body(Map.of("language", "PL"))
				.when()
				.patch("/api/users/me")
				.then()
				.statusCode(200);

		newBrowser();
		login(email, "correct-horse").statusCode(201).body("language", equalTo("PL"));
	}

	/**
	 * An unknown wire literal never deserializes, so the request never becomes content: <b>400</b>, the
	 * same split {@code GoalApiTest} pins for the goal enums. A missing field does parse and fails the
	 * contract's constraints instead, which is 422.
	 */
	@Test
	void rejectsAnUnknownLanguageLiteralWith400() {
		givenLoggedInUser();

		csrfAware()
				.body(Map.of("language", "FR"))
				.when()
				.patch("/api/users/me")
				.then()
				.statusCode(400)
				.contentType("application/problem+json");
	}

	@Test
	void rejectsALanguageSwitchWithNoLanguageWith422() {
		givenLoggedInUser();

		csrfAware()
				.body(Map.of())
				.when()
				.patch("/api/users/me")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");
	}

	@Test
	void rejectsALanguageSwitchCarryingNoCsrfToken() {
		givenLoggedInUser();

		client()
				.contentType("application/json")
				.body(Map.of("language", "PL"))
				.when()
				.patch("/api/users/me")
				.then()
				.statusCode(403);
	}

	/**
	 * The account erased between this session being established and this request arriving. It is a
	 * narrow race, but the branch it takes is worth pinning because <em>how</em> it becomes a 401 is
	 * not obvious: {@code UserSettingsService} raises an {@code AuthenticationException}, which
	 * survives only because {@code ApiExceptionHandler}'s inherited type list does not cover it and
	 * because {@code ExceptionTranslationFilter} unwraps the {@code ServletException} to find it. Both
	 * are one {@code @ExceptionHandler(Exception.class)} away from silently becoming a 500 — and
	 * {@code CurrentUser.requireId()} reaches its own 401 through the same two mechanisms, so this
	 * case guards both.
	 */
	@Test
	void answers401WhenTheAccountWasErasedUnderTheSession() {
		String email = givenLoggedInUser();
		jdbc.update("delete from ai_memory where user_id = (select id from app_user where email = ?)", email);
		jdbc.update("delete from remember_me_token where user_id = (select id from app_user where email = ?)", email);
		jdbc.update("delete from app_user where email = ?", email);

		csrfAware()
				.body(Map.of("language", "PL"))
				.when()
				.patch("/api/users/me")
				.then()
				.statusCode(401)
				.contentType("application/problem+json");
	}

	/**
	 * The regression the language switch makes possible. {@code SessionRegistry} is keyed on the
	 * principal, registered once at login, and the switch replaces the principal mid-session. With the
	 * record's generated component-wise equality the sweep would look up a key that no longer matches,
	 * find nothing, and leave a phone authenticating as a deleted account until its idle timeout —
	 * silently, since deletion itself would still return 204.
	 */
	@Test
	void endsEverySessionAfterALanguageSwitchHasReplacedThePrincipal() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		login(email, "correct-horse").statusCode(201);
		CookieFilter phone = currentBrowser();

		newBrowser();
		login(email, "correct-horse").statusCode(201);
		csrfAware()
				.body(Map.of("language", "PL"))
				.when()
				.patch("/api/users/me")
				.then()
				.statusCode(200);
		csrfAware()
				.body(Map.of("password", "correct-horse"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(204);

		switchToBrowser(phone);
		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(401);
	}

	@Test
	void rejectsAnOverlongAccountDeletionPasswordAsUnprocessable() {
		givenLoggedInUser();

		csrfAware()
				.body(Map.of("password", "😀".repeat(19)))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");
	}

	// --- Sign-in by mailed code (passwordless sign-in, release 1) ---

	/**
	 * A verified account asks for a code and exchanges it for a session through the same path a
	 * password takes: the session id rotates, the CSRF token rotates and is usable straight from the
	 * response, and the session reads as the account it was opened for.
	 */
	@Test
	void signsInAVerifiedAccountWithAMailedCode() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		String passwordSession = login(email, "correct-horse").statusCode(201).extract().cookie("JSESSIONID");
		String code = mailedSignInCode(email);
		String preSignInToken = primeCsrfToken();

		var response = signInWithCode(email, code)
				.statusCode(201)
				.header("Location", "/api/sessions/current")
				.body("email", equalTo(email))
				.extract();

		assertThat(response.cookie("JSESSIONID")).as("the session id rotates")
				.isNotBlank().isNotEqualTo(passwordSession);
		String rotated = response.cookie("XSRF-TOKEN");
		assertThat(rotated).as("a fresh CSRF token rides on the response").isNotBlank()
				.isNotEqualTo(preSignInToken);
		client().when().get("/api/users/me").then().statusCode(200).body("email", equalTo(email));
		client()
				.header("X-XSRF-TOKEN", rotated)
				.when()
				.delete("/api/sessions/current")
				.then()
				.statusCode(204);
	}

	/**
	 * Every way a code can fail on {@code POST /api/sessions} — wrong, expired, already spent, past its
	 * fifth guess, or for an address with no account — is the same 401 a wrong password is. Any
	 * difference in the body would be an oracle for which of them it was.
	 */
	@Test
	void answersEveryFailedCodeWithTheSameGeneric401() {
		String unknown = signInWithCode(uniqueEmail(), "12345678").statusCode(401).extract().asString();

		String email = uniqueEmail();
		register(email, "correct-horse");
		String wrongPassword = login(email, "not-the-password").statusCode(401).extract().asString();

		String code = mailedSignInCode(email);
		String wrong = signInWithCode(email, wrongCodeFor(code)).statusCode(401).extract().asString();

		signInWithCode(email, code).statusCode(201);
		newBrowser();
		String spent = signInWithCode(email, code).statusCode(401).extract().asString();

		String expiring = mailedSignInCode(email);
		jdbc.update("update app_user set verification_expires_at = now() - interval '1 minute' where email = ?",
				email);
		String expired = signInWithCode(email, expiring).statusCode(401).extract().asString();

		String capped = mailedSignInCode(email);
		for (int guess = 0; guess < 5; guess++) {
			signInWithCode(email, wrongCodeFor(capped)).statusCode(401);
		}
		String sixth = signInWithCode(email, capped).statusCode(401).extract().asString();

		assertThat(List.of(unknown, wrong, spent, expired, sixth))
				.as("no failed code can be told apart from a wrong password")
				.containsOnly(wrongPassword);
	}

	/** Four wrong guesses leave the fifth working — a cap of one would also pass the case above. */
	@Test
	void stillSignsInWithTheRightCodeAfterFourWrongOnes() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		String code = mailedSignInCode(email);

		for (int guess = 0; guess < 4; guess++) {
			signInWithCode(email, wrongCodeFor(code)).statusCode(401);
		}

		signInWithCode(email, code).statusCode(201);
	}

	/**
	 * The cap holds under a race. Twenty guesses fired at once all pass any read-then-check a caller
	 * could do; only the atomic charge in the database keeps the count at five — and once it is five,
	 * the right code is refused like any other.
	 */
	@Test
	void neverChargesMoreThanFiveGuessesToOneCodeUnderParallelAttempts() throws Exception {
		String email = uniqueEmail();
		register(email, "correct-horse");
		String code = mailedSignInCode(email);
		String token = primeCsrfToken();
		String wrong = wrongCodeFor(code);

		var pool = Executors.newFixedThreadPool(20);
		try {
			var guesses = IntStream.range(0, 20)
					.mapToObj(i -> pool.submit(() -> anonymous()
							.cookie("XSRF-TOKEN", token)
							.header("X-XSRF-TOKEN", token)
							.body(Map.of("email", email, "code", wrong))
							.post("/api/sessions")
							.statusCode()))
					.toList();
			for (var guess : guesses) {
				assertThat(guess.get()).isEqualTo(401);
			}
		}
		finally {
			pool.shutdownNow();
		}

		assertThat(jdbc.queryForObject("select verification_attempts from app_user where email = ?",
				Integer.class, email)).isEqualTo(5);
		signInWithCode(email, code).statusCode(401);
	}

	/** One credential per request: both, or neither, is a malformed request rather than a guess. */
	@Test
	void rejectsASignInCarryingBothOrNeitherCredentialAsUnprocessable() {
		String email = uniqueEmail();

		csrfAware()
				.body(Map.of("email", email, "password", "correct-horse", "code", "12345678"))
				.when()
				.post("/api/sessions")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");
		csrfAware()
				.body(Map.of("email", email))
				.when()
				.post("/api/sessions")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");
	}

	@Test
	void rejectsACodeThatIsNotEightDigitsAsUnprocessable() {
		signInWithCode(uniqueEmail(), "1234567")
				.statusCode(422)
				.contentType("application/problem+json");
	}

	/**
	 * The expand half of the rollout: release 2 writes accounts with no password, and a rollback to
	 * this image must still sign them in — by code — while a password attempt against them is the
	 * ordinary 401, never a 500.
	 */
	@Test
	void signsInAnAccountWithNoPasswordByCodeAndRefusesAPasswordForIt() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		jdbc.update("update app_user set password_hash = null where email = ?", email);

		login(email, "correct-horse")
				.statusCode(401)
				.contentType("application/problem+json");

		newBrowser();
		signInWithCode(email, mailedSignInCode(email)).statusCode(201).body("email", equalTo(email));
	}

	/**
	 * Picked out by name: the same response also cancels the remember-me cookie, and REST Assured's
	 * own header matcher only ever reads the last {@code Set-Cookie}.
	 */
	private static void assertSessionCookieCancelled(List<String> setCookies) {
		assertThat(setCookies).anySatisfy(cookie -> assertThat(cookie)
				.startsWith("JSESSIONID=;")
				.contains("Expires=Thu, 01 Jan 1970"));
	}
}
