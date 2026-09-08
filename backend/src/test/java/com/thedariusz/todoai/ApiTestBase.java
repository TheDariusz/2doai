package com.thedariusz.todoai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;
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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.yaml.snakeyaml.Yaml;

import static io.restassured.RestAssured.given;
import static org.assertj.core.api.Assertions.assertThat;

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

	/** The fake SMTP adapter from {@link TestcontainersConfiguration} — where the codes come from. */
	@Autowired
	protected TestcontainersConfiguration.RecordingEmailSender mail;

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

	/**
	 * Registers <em>and verifies</em> — the whole of what it takes to end up with an account that can
	 * log in (DEV-51). Every suite calls it and none of them had to change: the code is read out of the
	 * message the fake adapter captured and posted back through the real endpoint, so what these tests
	 * get is an account produced exactly the way a user's is, not one nudged into shape from the side.
	 */
	protected void register(String email, String password) {
		csrfAware()
				.body(Map.of("email", email, "password", password))
				.when()
				.post("/api/users")
				.then()
				.statusCode(201);
		confirmAddress(email);
	}

	/**
	 * Types the code this address was last sent. Fails loudly if it was never sent one.
	 *
	 * <p>Not {@code verify} — several suites statically import Mockito's, and a same-named method on
	 * a superclass shadows a static import rather than overloading it.
	 */
	protected void confirmAddress(String email) {
		String code = mail.codeFor(email)
				.orElseThrow(() -> new AssertionError("No verification code was mailed to " + email));
		csrfAware()
				.body(Map.of("email", email, "code", code))
				.when()
				.post("/api/verifications")
				.then()
				.statusCode(204);
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

	/**
	 * The contract, read from disk.
	 *
	 * <p>Several suites hold a wire literal against {@code openapi.yaml} — it is the anchor, and each
	 * side of the stack is checked against it rather than against another copy of itself (lessons.md,
	 * "A contract value duplicated across the stack needs one guard that spans the boundary"). The
	 * path and the navigation below are the part of that which is not specific to any one literal, so
	 * they live here instead of once per suite.
	 */
	protected static final String OPENAPI = "../context/foundation/openapi.yaml";

	protected static String read(String path) throws IOException {
		return Files.readString(Path.of(path));
	}

	protected static Map<String, Object> openApi() throws IOException {
		return new Yaml().load(read(OPENAPI));
	}

	/**
	 * Each enum is a <em>named</em> schema the operations {@code $ref}, so the anchor holds one copy of
	 * each list rather than repeating it across every request and response body — copies inside the
	 * anchor would reintroduce, within the spec itself, the drift these guards exist to catch.
	 */
	@SuppressWarnings("unchecked")
	protected static List<String> extensibleEnum(Map<String, Object> spec, String schema) {
		return (List<String>) schema(spec, schema).get("x-extensible-enum");
	}

	@SuppressWarnings("unchecked")
	protected static Map<String, Object> schema(Map<String, Object> spec, String name) {
		Map<String, Object> components = (Map<String, Object>) spec.get("components");
		Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
		Map<String, Object> target = (Map<String, Object>) schemas.get(name);
		assertThat(target).as("schema %s is missing from openapi.yaml", name).isNotNull();
		return target;
	}

	protected static List<String> constantNames(Enum<?>[] constants) {
		return Arrays.stream(constants).map(Enum::name).toList();
	}
}
