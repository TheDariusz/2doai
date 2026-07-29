package com.thedariusz.todoai;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.Cookie;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Import;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * End-to-end HTTP tests of the account lifecycle — register, log in, read the current user, log
 * out, delete — driven with <b>REST Assured</b> against a real embedded server so the full Spring
 * Security filter chain (CSRF, session cookie, authorization) is genuinely in the path. MockMvc is
 * not used anywhere in this project.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthApiTest extends ApiTestBase {

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

		csrfAware()
				.when()
				.delete("/api/sessions/current")
				.then()
				.statusCode(204)
				.header("Set-Cookie", allOf(
						containsString("JSESSIONID="),
						containsString("Expires=Thu, 01 Jan 1970")));

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
				.body("status", equalTo(403));
	}

	@Test
	void deletesTheAccountInvalidatesTheSessionAndFreesTheEmail() {
		String email = givenLoggedInUser();

		csrfAware()
				.body(Map.of("password", "correct-horse"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(204)
				.header("Set-Cookie", allOf(
						containsString("JSESSIONID="),
						containsString("Expires=Thu, 01 Jan 1970")));

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
				.body("detail", containsString("password"));

		// And the session survives — the user is still logged in, free to retry.
		client()
				.when()
				.get("/api/users/me")
				.then()
				.statusCode(200);
	}

	/**
	 * FR-019 says the account is erased, so no device may still be holding an authenticated session
	 * for it. {@code logout} only ends the calling request's session, so without expiring the rest a
	 * phone keeps returning 200 for a deleted account until its 30-minute idle timeout.
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
	 * hit would pin 30 minutes of in-memory session on the single Fly machine — an unauthenticated
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
}
