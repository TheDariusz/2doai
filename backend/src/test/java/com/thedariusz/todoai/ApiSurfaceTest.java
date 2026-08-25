package com.thedariusz.todoai;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;
import org.yaml.snakeyaml.Yaml;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The spec is the contract (Zalando #100/#101), which is only true while it describes <em>every</em>
 * path the server publishes. Nothing enforced that until now, and the gap showed: {@code /api/ping}
 * shipped in S-01, became load-bearing for the deployment smoke check and for CSRF priming in this
 * very test suite, and stayed out of {@code openapi.yaml} for four slices — invisible to every green
 * suite, because each side only ever asserted against its own copy of the truth.
 *
 * <p>Compared as a <b>set in both directions</b> on purpose. A subset check one way misses a new
 * controller; the other way misses a path deleted from the code and left in the spec. Both are the
 * same failure: a reader who trusts the file learns something untrue.
 *
 * <p>Only {@code /api/**} is compared — the spec's server base path. {@code /error} and
 * {@code /actuator/**} are infrastructure the SPA never calls and the contract never promises.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class ApiSurfaceTest extends ApiTestBase {

	private static final String BASE_PATH = "/api";

	/** Qualified: the actuator contributes a second {@code RequestMappingHandlerMapping} of its own. */
	@Autowired
	@Qualifier("requestMappingHandlerMapping")
	private RequestMappingHandlerMapping handlerMapping;

	@Test
	@SuppressWarnings("unchecked")
	void specifiesEveryPathTheServerPublishes() throws IOException {
		Map<String, Object> spec = new Yaml().load(
				Files.readString(Path.of("../context/foundation/openapi.yaml")));
		Set<String> specified = ((Map<String, Object>) spec.get("paths")).keySet();

		Set<String> published = handlerMapping.getHandlerMethods().keySet().stream()
				.flatMap(mapping -> mapping.getPathPatternsCondition() == null ? Stream.<String>empty()
						: mapping.getPathPatternsCondition().getPatternValues().stream())
				.filter(pattern -> pattern.startsWith(BASE_PATH + "/"))
				.map(pattern -> pattern.substring(BASE_PATH.length()))
				.collect(Collectors.toSet());

		assertThat(published)
				.as("openapi.yaml declares exactly the /api paths the controllers publish")
				.containsExactlyInAnyOrderElementsOf(specified);
	}
}
