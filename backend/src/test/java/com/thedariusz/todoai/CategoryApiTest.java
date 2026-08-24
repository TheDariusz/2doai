package com.thedariusz.todoai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;

/**
 * The read-only categories resource the frontend navigation is built from. Two contract details
 * matter beyond "it returns data": the collection is wrapped in a top-level object (Zalando #110,
 * so fields can be added later without breaking clients), and properties are snake_case (#118) —
 * both are things a client hard-codes and a silent change would break.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class CategoryApiTest extends ApiTestBase {

	@Test
	void returnsTheElevenDomainsInDisplayOrder() {
		givenLoggedInUser();

		client()
				.when()
				.get("/api/categories")
				.then()
				.statusCode(200)
				.body("items", hasSize(11))
				.body("items[0].code", equalTo("HEALTH"))
				.body("items[0].name", equalTo("Zdrowie"))
				.body("items.display_order", contains(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
	}

	/**
	 * Zalando #227 — a cacheable GET must say so. Eleven rows a Flyway seed owns, held in a field
	 * since startup and refetched by the SPA on every load; {@code private} because the collection is
	 * served behind the session cookie and must not land in a shared cache. Asserted loosely on the
	 * two directives that matter: Spring Security writes its own no-store header unless one is
	 * already present, so this also proves the controller's header is the one that survives the
	 * filter chain.
	 */
	@Test
	void saysTheReferenceDataMayBeCached() {
		givenLoggedInUser();

		client()
				.when()
				.get("/api/categories")
				.then()
				.statusCode(200)
				.header("Cache-Control", containsString("max-age=3600"))
				.header("Cache-Control", containsString("private"));
	}

	@Test
	void isGatedBehindAuthentication() {
		client()
				.when()
				.get("/api/categories")
				.then()
				.statusCode(401);
	}
}
