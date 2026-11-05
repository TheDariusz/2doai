package com.thedariusz.todoai;

import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * In production every log line is shipped to a third party (Grafana Cloud Loki), so the shared
 * contract's "never an email address, never user-typed text" is a property of the whole log, not
 * only of the two lines this package writes. These are the two server-side paths that used to break
 * it: Postgres's error detail on a duplicate sign-up, and the scoped-lookup 404 that logged the
 * request's client details — the session id (a bearer credential) and the remote user (the address).
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
@ExtendWith(OutputCaptureExtension.class)
class LogPrivacyApiTest extends ApiTestBase {

	@Test
	void aDuplicateSignUpLogsNoAddress(CapturedOutput output) {
		String email = uniqueEmail();
		register(email, "correct-horse");

		newBrowser();
		csrfAware()
				.body(Map.of("email", email, "password", "correct-horse"))
				.when()
				.post("/api/users")
				.then()
				.statusCode(409);

		assertThat(output.getAll()).doesNotContain(email);
	}

	@Test
	void aScopedLookupMissLogsTheAccountIdButNeitherTheSessionNorTheAddress(CapturedOutput output) {
		String email = givenLoggedInUser();
		String accountId = client().when().get("/api/users/me").then().statusCode(200).extract().path("id");

		csrfAware().when().delete("/api/goals/" + UUID.randomUUID()).then().statusCode(404);

		assertThat(output.getAll())
			.contains("Scoped lookup missed")
			.contains("account " + accountId)
			.doesNotContain(email)
			.doesNotContain("session=");
	}

}
