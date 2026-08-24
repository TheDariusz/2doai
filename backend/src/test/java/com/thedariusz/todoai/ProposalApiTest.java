package com.thedariusz.todoai;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.hamcrest.Matchers.equalTo;

/**
 * End-to-end HTTP tests of the proposals resource (S-04a, FR-015) — the engine reached through the
 * real filter chain, session cookie and CSRF double-submit included, as every API test here is.
 *
 * <p>The fixtures drive the heuristic through a task's {@code due_date} rather than through idle
 * time, because idle time is {@code @UpdateTimestamp}: a row created by the API is always zero days
 * old, and no amount of HTTP can age it. An overdue term is the one neglect signal a test can
 * genuinely produce over the wire — which is also why the layer that carries one feeds the
 * heuristic. The thresholds themselves belong to {@code ProposalSelectorTest}, where they cost
 * nothing to state.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ProposalApiTest extends ApiTestBase {

	/** A HashMap, not Map.of — a non-TASK layer's absent fields are explicit JSON nulls. */
	private static Map<String, Object> task(String content, String category, LocalDate dueDate) {
		Map<String, Object> body = new HashMap<>();
		body.put("content", content);
		body.put("layer", "TASK");
		body.put("horizon", null);
		body.put("category_code", category);
		body.put("due_date", dueDate.toString());
		return body;
	}

	private String createTask(Map<String, Object> payload) {
		return csrfAware()
				.body(payload)
				.when()
				.post("/api/goals")
				.then()
				.statusCode(201)
				.extract()
				.path("id");
	}

	@Test
	void proposesTheEntryWhoseTermHasAlreadyPassed() {
		givenLoggedInUser();
		// The overdue one is created first: the repository sorts createdAt DESC, so if it were
		// created last, "return the newest row" would satisfy this assertion just as well.
		String overdue = createTask(task("Oddać książkę", "EDUCATION", LocalDate.now().minusDays(2)));
		createTask(task("Zapłacić ZUS", "FINANCE", LocalDate.now().plusDays(3)));

		csrfAware()
				.when()
				.post("/api/proposals")
				.then()
				.statusCode(200)
				.body("entry.id", equalTo(overdue))
				.body("entry.content", equalTo("Oddać książkę"))
				.body("entry.layer", equalTo("TASK"))
				.body("neglected_days", equalTo(0));
	}

	@Test
	void answersNoContentWhileNothingHasBeenNeglected() {
		givenLoggedInUser();
		createTask(task("Kupić mleko", "HOME", LocalDate.now().plusDays(1)));

		csrfAware()
				.when()
				.post("/api/proposals")
				.then()
				.statusCode(204);
	}

	@Test
	void neverProposesAnotherAccountsNeglectedEntry() {
		givenLoggedInUser();
		createTask(task("Oddać książkę", "EDUCATION", LocalDate.now().minusDays(2)));

		newBrowser();
		givenLoggedInUser();
		String ownOverdue = createTask(task("Wymienić opony", "TRANSPORT", LocalDate.now().minusDays(5)));

		// The second account has a neglected entry of its own, so a 204 could not be mistaken for
		// "the engine returns empty regardless" — it must propose its own row, never the first's.
		csrfAware()
				.when()
				.post("/api/proposals")
				.then()
				.statusCode(200)
				.body("entry.id", equalTo(ownOverdue))
				.body("entry.content", equalTo("Wymienić opony"));
	}

	@Test
	void refusesAnonymousAccess() {
		// A fresh browser that never logged in, but carrying a real CSRF token — /api/ping primes one
		// for anonymous clients, which is what registration relies on. Without it the CSRF filter
		// would answer 403 first and this would prove nothing about authentication.
		newBrowser();

		csrfAware().when().post("/api/proposals").then().statusCode(401);
	}

	@Test
	void refusesAProposalWithoutTheCsrfToken() {
		givenLoggedInUser();

		// The spec publishes 403 on this path; being a POST it is a mutation like any other.
		client().when().post("/api/proposals").then().statusCode(403);
	}
}
