package com.thedariusz.todoai;

import java.util.Map;
import java.util.UUID;

import io.restassured.RestAssured;
import io.restassured.filter.cookie.CookieFilter;
import io.restassured.http.ContentType;
import io.restassured.response.Response;
import io.restassured.response.ValidatableResponse;
import io.restassured.specification.RequestSpecification;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.springframework.boot.test.web.server.LocalServerPort;

import static io.restassured.RestAssured.given;

/**
 * Shared browser-like client for the REST Assured endpoint tests: it carries cookies, primes and
 * echoes the CSRF token, and can register and log in. Subclasses add {@code @SpringBootTest} and
 * the assertions.
 *
 * <p>Everything here mimics what the SPA does, so the tests fail the same way a real client would —
 * in particular the CSRF double-submit, which is easy to satisfy accidentally in a mock and then
 * discover broken in a browser.
 */
abstract class ApiTestBase {

	@LocalServerPort
	private int port;

	/**
	 * A {@link CookieFilter}, not a {@code SessionFilter}: the latter carries only {@code JSESSIONID},
	 * but the double-submit check needs the {@code XSRF-TOKEN} cookie back on the request too — it is
	 * the <em>expected</em> value {@code CookieCsrfTokenRepository} compares the header against.
	 */
	private CookieFilter cookies;

	private String csrfToken;

	@BeforeEach
	void setUpClient() {
		RestAssured.port = port;
		newBrowser();
	}

	@AfterEach
	void resetRestAssured() {
		RestAssured.reset();
	}

	/** Start from a clean client — no session cookie, no CSRF token — as a fresh browser would. */
	protected void newBrowser() {
		cookies = new CookieFilter();
		csrfToken = null;
	}

	/** The current client's cookie jar, so a test can hold two logged-in devices at once. */
	protected CookieFilter currentBrowser() {
		return cookies;
	}

	/** Switch back to a jar captured earlier by {@link #currentBrowser()}. */
	protected void switchToBrowser(CookieFilter jar) {
		cookies = jar;
		csrfToken = null;
	}

	/** A request spec carrying the cookie jar, for reads (no CSRF token needed). */
	protected RequestSpecification client() {
		return given().filter(cookies);
	}

	/**
	 * A request spec carrying the cookie jar and the CSRF token echoed as {@code X-XSRF-TOKEN} — the
	 * double-submit the SPA performs on every mutation.
	 *
	 * <p>The token is cached because the server only sends {@code Set-Cookie: XSRF-TOKEN} when the
	 * token <em>changes</em>: the priming request issues one, later requests reuse it silently.
	 */
	protected RequestSpecification csrfAware() {
		return client().header("X-XSRF-TOKEN", primeCsrfToken()).contentType(ContentType.JSON);
	}

	/** The current CSRF token for this client, priming one on the first call. */
	protected String primeCsrfToken() {
		if (csrfToken == null) {
			csrfToken = client()
					.when()
					.get("/api/ping")
					.then()
					.statusCode(200)
					.extract()
					.cookie("XSRF-TOKEN");
		}
		return csrfToken;
	}

	protected void register(String email, String password) {
		csrfAware()
				.body(Map.of("email", email, "password", password))
				.when()
				.post("/api/users")
				.then()
				.statusCode(201);
	}

	/**
	 * Logs in and picks up the <em>rotated</em> CSRF token from the login response.
	 *
	 * <p>Authentication retires the anonymous token ({@code CsrfAuthenticationStrategy}) and
	 * {@code SessionController} materializes the replacement onto the same response, so a client
	 * reads its next token straight from the login reply — exactly what the SPA does. An empty
	 * cookie value means only the deletion arrived, and the cached token is dropped so the next
	 * mutation re-primes.
	 */
	protected ValidatableResponse login(String email, String password) {
		Response response = csrfAware()
				.body(Map.of("email", email, "password", password))
				.when()
				.post("/api/sessions");

		String rotated = response.getCookie("XSRF-TOKEN");
		csrfToken = (rotated == null || rotated.isEmpty()) ? null : rotated;
		return response.then();
	}

	/** A client with no cookie jar and no CSRF token at all — a stranger hitting the API cold. */
	protected RequestSpecification anonymous() {
		return given().contentType(ContentType.JSON);
	}

	/** Registers and logs in a fresh account, returning its email. */
	protected String givenLoggedInUser() {
		String email = uniqueEmail();
		register(email, "correct-horse");
		login(email, "correct-horse").statusCode(201);
		return email;
	}

	/** Each test uses its own account, so runs never collide on the {@code app_user.email} UNIQUE index. */
	protected static String uniqueEmail() {
		return "user-" + UUID.randomUUID() + "@example.com";
	}
}
