package com.thedariusz.todoai;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.hamcrest.Matchers.contains;
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
				.body("items[0].name_pl", equalTo("Zdrowie"))
				.body("items.display_order", contains(1, 2, 3, 4, 5, 6, 7, 8, 9, 10, 11));
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
