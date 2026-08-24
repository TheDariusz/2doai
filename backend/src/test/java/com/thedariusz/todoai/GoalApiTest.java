package com.thedariusz.todoai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import com.thedariusz.todoai.goal.Goal;
import com.thedariusz.todoai.goal.GoalHorizon;
import com.thedariusz.todoai.goal.GoalLayer;
import io.restassured.filter.cookie.CookieFilter;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;
import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;

/**
 * End-to-end HTTP tests of the goals resource (S-02/S-07, plus DEV-44's DELETE) — the four
 * operations that make up CRUD for all three layers, driven with REST Assured against a real embedded
 * server so the whole Spring Security filter chain (session cookie, CSRF double-submit) is in the
 * path. Lives in this package, like every other API test, because {@code ApiTestBase} is
 * package-private.
 *
 * <p>Two behaviours here are worth more than the happy paths. The <b>422/400 split</b>: a payload
 * that parses but breaks the layer × time-fields rule is unprocessable content (422), while an unknown
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

	/**
	 * A term rides beside the payload helpers rather than inside them: only a TASK may carry one, so
	 * every other test's entry legitimately has no {@code due_date} member at all.
	 */
	private static Map<String, Object> withDueDate(Map<String, Object> payload, String dueDate) {
		payload.put("due_date", dueDate);
		return payload;
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

	/** Every way to break the layer × time-fields rule, one per case so a failure names itself. */
	private static Stream<Map<String, Object>> inconsistentTimeFields() {
		return Stream.of(
				goalPayload("Cel bez horyzontu", "GOAL", null, null),
				goalPayload("Marzenie z horyzontem", "DREAM", "THIS_YEAR", null),
				goalPayload("Zadanie z horyzontem", "TASK", "THIS_YEAR", null),
				withDueDate(goalPayload("Cel z terminem", "GOAL", "THIS_YEAR", null), "2026-09-01"),
				withDueDate(goalPayload("Marzenie z terminem", "DREAM", null, null), "2026-09-01"));
	}

	/**
	 * The cross-field invariant is a <em>content</em> failure, not a syntax one: the body parsed
	 * cleanly and every field is individually well-typed. 422, and deliberately with a generic detail
	 * — {@code ApiExceptionHandler} drops field messages on purpose.
	 *
	 * <p>S-07 widened the rule rather than splitting it: "only a GOAL has a horizon" gained the mirror
	 * "only a TASK may have a due date", so both halves belong to one test rather than two named in
	 * two vocabularies.
	 */
	@ParameterizedTest
	@MethodSource("inconsistentTimeFields")
	void rejectsInconsistentTimeFieldsWith422(Map<String, Object> payload) {
		givenLoggedInUser();

		csrfAware()
				.body(payload)
				.when()
				.post("/api/goals")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");
	}

	/**
	 * The third layer (S-07, FR-003) on the same aggregate: a task is a goal's mirror image on the
	 * time fields — no horizon, and the only layer allowed a {@code due_date}. The term is
	 * <em>optional</em>, because most current tasks are "next", not "by Friday"; one that had to
	 * invent a deadline would be a worse todo list than the paper it replaces.
	 */
	@Test
	void createsATaskWithAndWithoutADueDate() {
		givenLoggedInUser();

		csrfAware()
				.body(withDueDate(goalPayload("Zapłacić za prąd", "TASK", null, "HOME"), "2026-09-01"))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(201)
				.body("layer", equalTo("TASK"))
				.body("horizon", nullValue())
				.body("due_date", equalTo("2026-09-01"))
				.body("category_code", equalTo("HOME"));

		csrfAware()
				.body(goalPayload("Kupić chleb", "TASK", null, null))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(201)
				.body("layer", equalTo("TASK"))
				.body("due_date", nullValue());
	}



	/**
	 * FR-014's soft dependency in miniature: a long-term goal that becomes a concrete next action is
	 * the same row changing layer. One full replace has to drop the horizon and pick up a term
	 * together — and refuse the halfway state where the row would carry both.
	 */
	@Test
	void convertsAGoalIntoATaskWithATerm() {
		givenLoggedInUser();
		String id = createGoal(goalPayload("Przebiec półmaraton", "GOAL", "THIS_YEAR", "HEALTH"));

		csrfAware()
				.body(withDueDate(updatePayload("Zapisać się na bieg", "TASK", null, "HEALTH", false),
						"2026-09-01"))
				.when()
				.put("/api/goals/" + id)
				.then()
				.statusCode(200)
				.body("layer", equalTo("TASK"))
				.body("horizon", nullValue())
				.body("due_date", equalTo("2026-09-01"));

		// And on the way back: a task that regains a horizon must shed the term in the same request.
		csrfAware()
				.body(withDueDate(updatePayload("Zapisać się na bieg", "GOAL", "THIS_YEAR", "HEALTH", false),
						"2026-09-01"))
				.when()
				.put("/api/goals/" + id)
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

	/**
	 * The length cap is a contract term the SPA also enforces client-side, so it is only reachable by
	 * a caller that bypasses the form — which is precisely why the server has to answer 422 rather
	 * than letting an over-long string reach the column and surface as a 500.
	 */
	@Test
	void rejectsContentLongerThanTheCapWith422() {
		givenLoggedInUser();

		csrfAware()
				.body(goalPayload("a".repeat(Goal.MAX_CONTENT_LENGTH + 1), "DREAM", null, null))
				.when()
				.post("/api/goals")
				.then()
				.statusCode(422)
				.contentType("application/problem+json");
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
	 * The completion moment is data S-03 reads, not a flag. PUT is a full replace and the SPA sends
	 * an entry's own completion state back with every edit, so a re-stamp on each write would move
	 * the date every time someone fixes a typo — and the original moment is unrecoverable.
	 */
	@Test
	void keepsTheOriginalCompletionMomentWhenACompletedEntryIsEdited() {
		givenLoggedInUser();
		String id = createGoal(goalPayload("Przeczytać 12 książek", "GOAL", "THIS_YEAR", "EDUCATION"));

		String completedAt = csrfAware()
				.body(updatePayload("Przeczytać 12 książek", "GOAL", "THIS_YEAR", "EDUCATION", true))
				.when()
				.put("/api/goals/" + id)
				.then()
				.statusCode(200)
				.extract()
				.path("completed_at");

		String afterTheEdit = csrfAware()
				.body(updatePayload("Przeczytać 12 książek (poprawka)", "GOAL", "THIS_YEAR", "EDUCATION", true))
				.when()
				.put("/api/goals/" + id)
				.then()
				.statusCode(200)
				.body("content", equalTo("Przeczytać 12 książek (poprawka)"))
				.extract()
				.path("completed_at");

		// Compared as instants: a freshly written row serializes in the server's offset, one read back
		// from timestamptz comes out as UTC, and both are the same moment.
		assertThat(OffsetDateTime.parse(afterTheEdit).toInstant())
				.as("editing a completed entry must not move its completion moment")
				.isEqualTo(OffsetDateTime.parse(completedAt).toInstant());
	}

	/**
	 * Someone else's id and an id that never existed must be indistinguishable — otherwise a caller
	 * can probe which UUIDs belong to other accounts by watching the status code or the body. Both
	 * id-addressed operations are held to it: PUT and, since DEV-44, DELETE. They are <em>separate</em>
	 * derived queries — {@code findByIdAndUserId} and {@code deleteByIdAndUserId} — so the property is
	 * re-asserted per method rather than inherited, which is why both verbs are probed here.
	 *
	 * <p>The last assertion is the one a status-code check cannot make: Alice's goal is still there.
	 * A DELETE that answered 404 and removed the row anyway would satisfy everything above it.
	 */
	@Test
	void answersIdenticallyForAForeignGoalAndAGoalThatNeverExisted() {
		givenLoggedInUser();
		String aliceGoal = createGoal(goalPayload("Cel Alicji", "GOAL", "THIS_YEAR", "HEALTH"));
		CookieFilter alice = currentBrowser();

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

		String foreignDelete = csrfAware()
				.when().delete("/api/goals/" + aliceGoal)
				.then().statusCode(404).contentType("application/problem+json")
				.extract().asString();
		String nonexistentDelete = csrfAware()
				.when().delete("/api/goals/" + UUID.randomUUID())
				.then().statusCode(404)
				.extract().asString();

		assertThat(withoutInstance(foreignDelete))
				.as("delete is no existence oracle either")
				.isEqualTo(withoutInstance(nonexistentDelete));

		switchToBrowser(alice);
		client().when().get("/api/goals").then().statusCode(200).body("items", hasSize(1));
	}

	/**
	 * DEV-44 closes CRUD. A hard delete: the row is gone, not withdrawn, so the id stops resolving
	 * entirely and a second delete is indistinguishable from any other id the caller does not own.
	 *
	 * <p>The second goal is the point of the fixture. With one row, "deleted the addressed goal" and
	 * "deleted every goal I own" are the same assertion — and it is the destructive direction that
	 * loses data. The surviving entry is named, so a delete that took both fails here.
	 */
	@Test
	void deletesTheCallersOwnGoal() {
		givenLoggedInUser();
		String id = createGoal(goalPayload("Cel do usunięcia", "GOAL", "THIS_YEAR", "HEALTH"));
		createGoal(goalPayload("Cel do zachowania", "GOAL", "THIS_YEAR", "HEALTH"));

		csrfAware().when().delete("/api/goals/" + id).then().statusCode(204);

		client().when().get("/api/goals").then().statusCode(200)
				.body("items", hasSize(1))
				.body("items[0].content", equalTo("Cel do zachowania"));
		csrfAware().when().delete("/api/goals/" + id).then().statusCode(404);
	}

	/**
	 * The two denials a destructive operation has to answer. They are <em>not</em> symmetric — the
	 * missing token is 403 for everyone and the 401 is only reachable <em>with</em> a valid token; the
	 * filter ordering behind that is pinned by
	 * {@code AuthApiTest.rejectsAnAuthenticatedMutationCarryingNoCsrfToken}.
	 *
	 * <p>Both probes end with the row still there: a delete that answered 403 or 401 and deleted
	 * anyway would pass a status-code-only assertion.
	 */
	@Test
	void deniesADeleteWithoutACsrfTokenOrWithoutASessionAndKeepsTheEntry() {
		givenLoggedInUser();
		String id = createGoal(goalPayload("Cel nie do ruszenia", "GOAL", "THIS_YEAR", "HEALTH"));
		CookieFilter owner = currentBrowser();

		client().when().delete("/api/goals/" + id).then().statusCode(403);

		newBrowser();
		csrfAware().when().delete("/api/goals/" + id).then().statusCode(401);

		switchToBrowser(owner);
		client().when().get("/api/goals").then().statusCode(200).body("items", hasSize(1));
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
	 * <p>The frontend leg <b>parses the union out of the SPA's {@code Goal} type</b> rather than
	 * searching the file for the literals. A whole-file search is the same trap in a different shape:
	 * each literal also appears in {@code LAYER_LABEL}/{@code HORIZON_LABEL} and in
	 * {@code layer === 'GOAL'}, so widening the type to {@code layer: string} — losing the contract
	 * entirely — would leave every literal present and the search green.
	 *
	 * <p>It lives here rather than in Vitest because only this side can see all three copies at once:
	 * the spec and the SPA both sit above the frontend package root (same reason as
	 * {@code AuthApiTest.emitsTheReAuthUrnTheContractAndTheSpaBothHardcode}).
	 */
	@Test
	void publishesTheWireEnumsTheContractAnchors() throws IOException {
		Map<String, Object> spec = new Yaml().load(Files.readString(
				Path.of("../context/foundation/openapi.yaml")));
		String spa = Files.readString(Path.of("../frontend/src/pages/GoalsPage.tsx"));

		assertThat(extensibleEnum(spec, "GoalLayer"))
				.as("openapi.yaml is the anchor for every wire literal the stack hardcodes")
				.containsExactlyInAnyOrderElementsOf(constantNames(GoalLayer.values()));
		assertThat(extensibleEnum(spec, "GoalHorizon"))
				.containsExactlyInAnyOrderElementsOf(constantNames(GoalHorizon.values()));
		String goalType = spaGoalType(spa);
		assertThat(unionLiterals(goalType, "layer"))
				.as("the SPA's goal type spells out the same layer literals it sends and switches on")
				.containsExactlyInAnyOrderElementsOf(constantNames(GoalLayer.values()));
		assertThat(unionLiterals(goalType, "horizon"))
				.as("the SPA's goal type spells out the same horizon literals")
				.containsExactlyInAnyOrderElementsOf(constantNames(GoalHorizon.values()));

		// The content limit spans four copies, not three. ddl-auto=validate does NOT pin the column
		// width to the mapping — Hibernate's validator compares JDBC type codes and ignores length,
		// so goal.content could be narrowed to VARCHAR(200) against a @Size(max = 500) and boot
		// cleanly, failing only at runtime on the first long value. Hence the migration is read here
		// like any other copy.
		assertThat(schema(spec, "GoalContent").get("maxLength"))
				.isEqualTo(Goal.MAX_CONTENT_LENGTH);
		assertThat(spa)
				.as("the SPA caps the same field at the same length the server enforces")
				.contains("maxLength={" + Goal.MAX_CONTENT_LENGTH + "}");
		assertThat(migrationContentWidth())
				.as("goal.content's column width is not pinned by ddl-auto=validate, only by this")
				.isEqualTo(Goal.MAX_CONTENT_LENGTH);
	}

	/** The declared width of {@code goal.content} in {@code V6} — the fourth copy of the cap. */
	private static int migrationContentWidth() throws IOException {
		String migration = Files.readString(
				Path.of("../backend/src/main/resources/db/migration/V6__create_goal.sql"));
		Matcher column = Pattern.compile("content\\s+VARCHAR\\((\\d+)\\)").matcher(migration);
		assertThat(column.find()).as("V6 declares goal.content as VARCHAR(n)").isTrue();
		return Integer.parseInt(column.group(1));
	}

	/** The body of {@code export type Goal = { … }}, so the search cannot stray into label maps. */
	private static String spaGoalType(String spa) {
		Matcher block = Pattern.compile("export type Goal = \\{(.*?)\\n\\}", Pattern.DOTALL).matcher(spa);
		assertThat(block.find()).as("the SPA exports a Goal type for the contract to anchor").isTrue();
		return block.group(1);
	}

	/** The quoted literals of one field's union — empty if the field was widened to a bare type. */
	private static List<String> unionLiterals(String goalType, String field) {
		Matcher declaration = Pattern.compile("^\\s*" + field + ": (.+)$", Pattern.MULTILINE)
				.matcher(goalType);
		assertThat(declaration.find()).as("the SPA's goal type declares %s", field).isTrue();

		Matcher literal = Pattern.compile("'([^']+)'").matcher(declaration.group(1));
		List<String> literals = new ArrayList<>();
		while (literal.find()) {
			literals.add(literal.group(1));
		}
		return literals;
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
		return (List<String>) schema(spec, schema).get("x-extensible-enum");
	}

	@SuppressWarnings("unchecked")
	private static Map<String, Object> schema(Map<String, Object> spec, String name) {
		Map<String, Object> components = (Map<String, Object>) spec.get("components");
		Map<String, Object> schemas = (Map<String, Object>) components.get("schemas");
		Map<String, Object> target = (Map<String, Object>) schemas.get(name);
		assertThat(target).as("schema %s is missing from openapi.yaml", name).isNotNull();
		return target;
	}

	/** The {@code instance} member carries the request path, which differs between the two probes. */
	private static String withoutInstance(String problemJson) {
		return problemJson.replaceAll("\"instance\"\\s*:\\s*\"[^\"]*\"", "");
	}
}
