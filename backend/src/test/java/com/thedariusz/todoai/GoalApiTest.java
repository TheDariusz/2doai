package com.thedariusz.todoai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.thedariusz.todoai.goal.GoalHorizon;
import com.thedariusz.todoai.goal.GoalLayer;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * End-to-end HTTP tests of the goals resource (S-02) — the three operations that make up
 * CRUD-minus-delete for both non-task layers, driven with REST Assured against a real embedded
 * server so the whole Spring Security filter chain (session cookie, CSRF double-submit) is in the
 * path. Lives in this package, like every other API test, because {@code ApiTestBase} is
 * package-private.
 *
 * <p>Two behaviours here are worth more than the happy paths. The <b>422/400 split</b>: a payload
 * that parses but breaks the layer × horizon rule is unprocessable content (422), while an unknown
 * wire literal never deserializes at all and is a malformed request (400) — clients branch on that
 * difference. And <b>404 indistinguishability</b>: someone else's goal and a goal that never existed
 * must answer identically, or the API becomes an existence oracle for other accounts' ids.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GoalApiTest extends ApiTestBase {

	/** A HashMap, not Map.of — the interesting payloads carry explicit JSON nulls. */
	private static Map<String, Object> goalPayload(String content, String layer, String horizon, String category) {
		Map<String, Object> body = new HashMap<>();
		body.put("content", content);
		body.put("layer", layer);
		body.put("horizon", horizon);
		body.put("category_code", category);
		return body;
	}

	private static Map<String, Object> updatePayload(String content, String layer, String horizon,
			String category, boolean completed) {
		Map<String, Object> body = goalPayload(content, layer, horizon, category);
		body.put("completed", completed);
		return body;
	}

	private String createGoal(Map<String, Object> payload) {
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
	void refusesAnonymousAccess() {
		anonymous().when().get("/api/goals").then().statusCode(401);
	}

	@Test
	void createsALongTermGoalWithAHorizonAndACategory() {
		givenLoggedInUser();

		csrfAware()
				.body(goalPayload("Przebiec półmaraton", "GOAL", "THIS_YEAR", "HEALTH"))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(201)
				.body("id", notNullValue())
				.body("content", equalTo("Przebiec półmaraton"))
				.body("layer", equalTo("GOAL"))
				.body("horizon", equalTo("THIS_YEAR"))
				.body("category_code", equalTo("HEALTH"))
				.body("completed_at", nullValue())
				.body("created_at", notNullValue())
				.body("updated_at", notNullValue());
	}

	@Test
	void createsADreamWithNeitherHorizonNorCategory() {
		givenLoggedInUser();

		csrfAware()
				.body(goalPayload("Pojechać do Japonii", "DREAM", null, null))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(201)
				.body("layer", equalTo("DREAM"))
				.body("horizon", nullValue())
				.body("category_code", nullValue());
	}

	/**
	 * The cross-field invariant is a <em>content</em> failure, not a syntax one: the body parsed
	 * cleanly and every field is individually well-typed. 422, and deliberately with a generic detail
	 * — {@code ApiExceptionHandler} drops field messages on purpose.
	 */
	@Test
	void rejectsALayerHorizonMismatchWith422() {
		givenLoggedInUser();

		csrfAware()
				.body(goalPayload("Cel bez horyzontu", "GOAL", null, null))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");

		csrfAware()
				.body(goalPayload("Marzenie z horyzontem", "DREAM", "THIS_YEAR", null))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(422);
	}

	@Test
	void rejectsBlankContentWith422() {
		givenLoggedInUser();

		csrfAware()
				.body(goalPayload("   ", "DREAM", null, null))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(422);
	}

	/** An unknown literal fails Jackson deserialization, so the request never becomes content: 400. */
	@Test
	void rejectsUnknownWireLiteralsWith400() {
		givenLoggedInUser();

		csrfAware()
				.body(goalPayload("Życzenie", "WISH", null, null))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(400);

		csrfAware()
				.body(goalPayload("Horoskop", "DREAM", null, "ASTROLOGY"))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(400);
	}

	@Test
	void listsOnlyTheCallersOwnGoals() {
		givenLoggedInUser();
		createGoal(goalPayload("Cel Alicji", "GOAL", "FEW_MONTHS", "CAREER"));

		newBrowser();
		givenLoggedInUser();
		createGoal(goalPayload("Marzenie Boba", "DREAM", null, null));

		client()
				.when()
				.get("/api/goals")
				.then()
				.statusCode(200)
				.body("items", hasSize(1))
				.body("items[0].content", equalTo("Marzenie Boba"));
	}

	@Test
	void editsContentAndCategory() {
		givenLoggedInUser();
		String id = createGoal(goalPayload("Przebiec półmaraton", "GOAL", "THIS_YEAR", "HEALTH"));

		csrfAware()
				.body(updatePayload("Przebiec maraton", "GOAL", "FEW_MONTHS", "LEISURE", false))
				.when()
				.put("/api/goals/" + id)
				.then()
				.statusCode(200)
				.body("content", equalTo("Przebiec maraton"))
				.body("horizon", equalTo("FEW_MONTHS"))
				.body("category_code", equalTo("LEISURE"));
	}

	/** The conversion path FR-004/FR-005 imply: a dream that acquires a timeframe becomes a goal. */
	@Test
	void convertsADreamIntoAGoal() {
		givenLoggedInUser();
		String id = createGoal(goalPayload("Pojechać do Japonii", "DREAM", null, null));

		csrfAware()
				.body(updatePayload("Pojechać do Japonii", "GOAL", "FEW_MONTHS", null, false))
				.when()
				.put("/api/goals/" + id)
				.then()
				.statusCode(200)
				.body("layer", equalTo("GOAL"))
				.body("horizon", equalTo("FEW_MONTHS"));

		// And the invariant still bites on the way back: a GOAL cannot shed its horizon.
		csrfAware()
				.body(updatePayload("Pojechać do Japonii", "GOAL", null, null, false))
				.when()
				.put("/api/goals/" + id)
				.then()
				.statusCode(422);
	}

	@Test
	void completesAndReopensAnEntry() {
		givenLoggedInUser();
		String id = createGoal(goalPayload("Przeczytać 12 książek", "GOAL", "THIS_YEAR", "EDUCATION"));

		csrfAware()
				.body(updatePayload("Przeczytać 12 książek", "GOAL", "THIS_YEAR", "EDUCATION", true))
				.when()
				.put("/api/goals/" + id)
				.then()
				.statusCode(200)
				.body("completed_at", notNullValue());

		csrfAware()
				.body(updatePayload("Przeczytać 12 książek", "GOAL", "THIS_YEAR", "EDUCATION", false))
				.when()
				.put("/api/goals/" + id)
				.then()
				.statusCode(200)
				.body("completed_at", nullValue());
	}

	/**
	 * Someone else's id and an id that never existed must be indistinguishable — otherwise a caller
	 * can probe which UUIDs belong to other accounts by watching the status code or the body.
	 */
	@Test
	void answersIdenticallyForAForeignGoalAndAGoalThatNeverExisted() {
		givenLoggedInUser();
		String aliceGoal = createGoal(goalPayload("Cel Alicji", "GOAL", "THIS_YEAR", "HEALTH"));

		newBrowser();
		givenLoggedInUser();
		Map<String, Object> edit = updatePayload("Podmiana", "GOAL", "THIS_YEAR", null, false);

		String foreign = csrfAware().body(edit)
				.when().put("/api/goals/" + aliceGoal)
				.then().statusCode(404).contentType("application/problem+json")
				.extract().asString();
		String nonexistent = csrfAware().body(edit)
				.when().put("/api/goals/" + UUID.randomUUID())
				.then().statusCode(404)
				.extract().asString();

		assertThat(withoutInstance(foreign))
				.as("a foreign goal and a nonexistent one must not be tellable apart")
				.isEqualTo(withoutInstance(nonexistent));
	}

	/** FR-019: deleting the account takes the goals with it, and the deleter is genuinely wired in. */
	@Test
	void erasesGoalsWhenTheAccountIsDeleted() {
		givenLoggedInUser();
		createGoal(goalPayload("Cel do skasowania", "GOAL", "THIS_YEAR", "HEALTH"));

		csrfAware()
				.body(Map.of("password", "correct-horse"))
				.when()
				.delete("/api/users/me")
				.then()
				.statusCode(204);

		// A fresh account sees an empty list — the rows are gone, not merely orphaned. Were
		// GoalDataDeleter missing, the delete above would have failed on goal's restricting FK.
		newBrowser();
		givenLoggedInUser();
		client().when().get("/api/goals").then().statusCode(200).body("items", hasSize(0));
	}

	/**
	 * The check that spans the boundary for S-02's two new wire enums (lessons.md). Each side would
	 * otherwise assert against its <em>own</em> copy of the literals, so every suite stays green while
	 * the copies disagree — the failure mode that let six of eleven category codes rot in the spec
	 * unnoticed. Here {@code openapi.yaml} is the anchor and the running code is held against it, so
	 * adding, removing or renaming a constant on either side goes red.
	 *
	 * <p>The set is compared, not merely searched for: a substring check would pass happily after a
	 * value was <em>deleted</em> from the spec.
	 *
	 * <p>The frontend leg of this guard — asserting the same literals appear in the SPA's goal type —
	 * arrives with the page itself in Phase 3, since there is nothing to read until then.
	 */
	@Test
	void publishesTheWireEnumsTheContractAnchors() throws IOException {
		Map<String, Object> spec = new Yaml().load(Files.readString(
				Path.of("../context/changes/account-and-auth/openapi.yaml")));

		assertThat(extensibleEnum(spec, "GoalLayer"))
				.as("openapi.yaml is the anchor for every wire literal the stack hardcodes")
				.containsExactlyInAnyOrderElementsOf(constantNames(GoalLayer.values()));
		assertThat(extensibleEnum(spec, "GoalHorizon"))
				.containsExactlyInAnyOrderElementsOf(constantNames(GoalHorizon.values()));
	}

	private static List<String> constantNames(Enum<?>[] constants) {
		return Arrays.stream(constants).map(Enum::name).toList();
	}

	/**
	 * Each enum is a <em>named</em> schema the operations {@code $ref}, so the anchor holds one copy of
	 * each list rather than repeating it across Goal / GoalCreation / GoalUpdate — three copies inside
	 * the anchor would reintroduce, within the spec itself, the drift this guard exists to catch.
	 */
	@SuppressWarnings("unchecked")
	private static List<String> extensibleEnum(Map<String, Object> spec, String schema) {
		Map<String, Object> components = (Map<String, Object>) spec.get("components");
		Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
		Map<String, Object> target = (Map<String, Object>) schemas.get(schema);
		assertThat(target).as("schema %s is missing from openapi.yaml", schema).isNotNull();
		return (List<String>) target.get("x-extensible-enum");
	}

	/** The {@code instance} member carries the request path, which differs between the two probes. */
	private static String withoutInstance(String problemJson) {
		return problemJson.replaceAll("\"instance\"\\s*:\\s*\"[^\"]*\"", "");
	}
}
