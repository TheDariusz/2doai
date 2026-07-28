package com.thedariusz.todoai;

import java.util.Locale;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

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
	 * The CSRF contract for an <em>authenticated</em> caller: 403, not 401. (An anonymous request
	 * failing CSRF gets 401 instead — Spring's {@code ExceptionTranslationFilter} sends anonymous
	 * users to the authentication entry point rather than the access-denied handler.)
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

	@Test
	void rejectsDeletionWhenTheReAuthPasswordIsWrong() {
		givenLoggedInUser();

		csrfAware()
				.body(Map.of("password", "not-the-password"))
				.when()
				.delete("/api/users/me")
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
}
