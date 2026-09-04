package com.thedariusz.todoai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.condition.RequestMethodsRequestCondition;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spec is the contract (Zalando #100/#101), which is only true while it describes <em>every</em>
 * operation the server publishes. Nothing enforced that until now, and the gap showed:
 * {@code /api/ping} shipped in S-01, became load-bearing for the deployment smoke check and for CSRF
 * priming in this very test suite, and stayed out of {@code openapi.yaml} for four slices —
 * invisible to every green suite, because each side only ever asserted against its own copy of the
 * truth.
 *
 * <p>Compared as a <b>set in both directions</b> on purpose. A subset check one way misses a new
 * controller; the other way misses an operation deleted from the code and left in the spec. Both are
 * the same failure: a reader who trusts the file learns something untrue.
 *
 * <p><b>The unit of comparison is the operation — method <em>and</em> path — not the path alone.</b>
 * It was the path alone until DEV-49, and that was weaker than it looked: {@code PATCH /users/me}
 * was added to a path the spec already declared for {@code GET} and {@code DELETE}, so the whole
 * endpoint could have shipped undocumented with this test still green. The plan for that slice
 * claimed the spec "moves in the same commit as the Java — {@code ApiSurfaceTest} fails until it
 * does", which was only ever true of a brand-new path. Comparing operations makes the claim true,
 * and matters more from here on: the remaining localization phases add methods and headers to paths
 * that already exist.
 *
 * <p>Only {@code /api/**} is compared — the spec's server base path. {@code /error} and
 * {@code /actuator/**} are infrastructure the SPA never calls and the contract never promises.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSurfaceTest extends ApiTestBase {

	private static final String BASE_PATH = "/api";

	/**
	 * The keys of an OpenAPI Path Item that are operations. The others ({@code summary},
	 * {@code description}, {@code servers}, {@code parameters}) describe the path itself and must not
	 * be read as verbs the server publishes.
	 */
	private static final Set<String> OPERATIONS =
			Set.of("get", "put", "post", "delete", "options", "head", "patch", "trace");

	/** Qualified: the actuator contributes a second {@code RequestMappingHandlerMapping} of its own. */
	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	void specifiesEveryOperationTheServerPublishes() throws IOException {
		assertThat(publishedOperations())
				.as("openapi.yaml declares exactly the /api operations the controllers publish")
				.containsExactlyInAnyOrderElementsOf(specifiedOperations());
	}

	@SuppressWarnings("unchecked")
	private static Set<String> specifiedOperations() throws IOException {
		Map<String, Object> spec = new Yaml().load(
				Files.readString(Path.of("../context/foundation/openapi.yaml")));
		Map<String, Object> paths = (Map<String, Object>) spec.get("paths");

		return paths.entrySet().stream()
				.flatMap(path -> ((Map<String, Object>) path.getValue()).keySet().stream()
						.filter(OPERATIONS::contains)
						.map(method -> operation(method.toUpperCase(Locale.ROOT), path.getKey())))
				.collect(Collectors.toSet());
	}

	private Set<String> publishedOperations() {
		return handlerMapping.getHandlerMethods().keySet().stream()
				.filter(mapping -> mapping.getPathPatternsCondition() != null)
				.flatMap(mapping -> mapping.getPathPatternsCondition().getPatternValues().stream()
						.filter(pattern -> pattern.startsWith(BASE_PATH + "/"))
						.flatMap(pattern -> methodsOf(mapping)
								.map(method -> operation(method, pattern.substring(BASE_PATH.length())))))
				.collect(Collectors.toSet());
	}

	/**
	 * A handler that declares no HTTP method answers all of them, which the spec cannot express as
	 * one entry. Nothing here does that today; reporting it as the unmatchable literal
	 * {@code ANY} makes the set comparison fail loudly rather than silently comparing the wrong thing.
	 */
	private static Stream<String> methodsOf(RequestMappingInfo mapping) {
		RequestMethodsRequestCondition methods = mapping.getMethodsCondition();
		return methods.getMethods().isEmpty()
				? Stream.of("ANY")
				: methods.getMethods().stream().map(Enum::name);
	}

	private static String operation(String method, String path) {
		return method + " " + path;
	}
}
