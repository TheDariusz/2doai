package com.thedariusz.todoai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static io.restassured.RestAssured.given;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.notNullValue;

/**
 * End-to-end HTTP tests of the security gateway, driven with <b>REST Assured</b> against a real
 * embedded server (random port) so the full Spring Security filter chain is genuinely in the path —
 * not a MockMvc mock servlet. Proves the public/gated boundary of {@link com.thedariusz.todoai.security.SecurityConfig}:
 *
 * <ul>
 *   <li>{@code GET /api/ping} is {@code permitAll} → 200 (the Pattern B smoke check);</li>
 *   <li>a gated path is 401 when anonymous — a status code, not an HTML login redirect;</li>
 *   <li>the {@code CsrfCookieFilter} primes the {@code XSRF-TOKEN} cookie even on that gated 401,
 *       so the SPA's first login POST can echo it.</li>
 * </ul>
 *
 * <p>The gated path {@code /api/secure/probe} has no handler on purpose: the authorization filter
 * rejects the anonymous request before dispatch, so 401 (not 404) proves gating without any stub
 * controller. The <em>authenticated</em> case is not exercised here — there is no login endpoint in
 * Phase 1; it is proven end-to-end (real login → session cookie → gated call → 200) by the Phase 2
 * REST Assured lifecycle test.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class SecurityGatewayApiTest extends ApiTestBase {

	@Test
	void pingIsPublicAndReturnsOk() {
		given()
				.when()
				.get("/api/ping")
				.then()
				.statusCode(200)
				.body("status", equalTo("ok"));
	}

	@Test
	void gatedPathIsUnauthorizedWhenAnonymous() {
		given()
				.when()
				.get("/api/secure/probe")
				.then()
				.statusCode(401);
	}

	@Test
	void csrfTokenCookieIsPrimedEvenOnAGated401() {
		given()
				.when()
				.get("/api/secure/probe")
				.then()
				.statusCode(401)
				.cookie("XSRF-TOKEN", notNullValue());
	}
}
