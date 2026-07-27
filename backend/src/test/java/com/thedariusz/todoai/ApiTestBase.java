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
		if (csrfToken == null) {
			csrfToken = client()
					.when()
					.get("/api/ping")
					.then()
					.statusCode(200)
					.extract()
					.cookie("XSRF-TOKEN");
		}
		return client().header("X-XSRF-TOKEN", csrfToken).contentType(ContentType.JSON);
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
	 * Logs in, then drops the cached CSRF token so the next mutation re-primes.
	 *
	 * <p>Authentication <em>retires</em> the anonymous token: {@code CsrfAuthenticationStrategy}
	 * clears the cookie and lets SS6 defer generating the replacement, so the login response carries
	 * a cookie deletion rather than a new token. The next request re-primes it via
	 * {@code CsrfCookieFilter}. <b>Phase 3 note:</b> the SPA must do the same — a client that caches
	 * the pre-login token and reuses it after login gets a 403 on its first mutation.
	 */
	protected ValidatableResponse login(String email, String password) {
		Response response = csrfAware()
				.body(Map.of("email", email, "password", password))
				.when()
				.post("/api/sessions");

		csrfToken = null;
		return response.then();
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
