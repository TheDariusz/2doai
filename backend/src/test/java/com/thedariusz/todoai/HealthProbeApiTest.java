package com.thedariusz.todoai;

import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.health.actuate.endpoint.HealthEndpointGroups;
import org.springframework.boot.health.registry.HealthContributorRegistry;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.equalTo;

/**
 * The probe Fly actually calls. {@code fly.toml} points its health check at
 * {@code /actuator/health/liveness} and never at the aggregate, because a sleeping or transient Neon
 * must not be able to restart the always-on machine the FR-011 scheduler lives on — and a restart
 * loop is indistinguishable, from outside, from an app that simply never comes back to you.
 *
 * <p>Membership is asserted against {@link HealthEndpointGroups} rather than by reading the response
 * body: the endpoint hides its components unless details are turned on, and turning them on for a
 * test would assert the test's configuration instead of the shipped one.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class HealthProbeApiTest extends ApiTestBase {

	@Autowired
	private HealthEndpointGroups groups;

	@Autowired
	private HealthContributorRegistry contributors;

	/**
	 * UP here means the tick has actually run: the indicator starts DOWN and only the scheduled method
	 * firing lifts it. So this is the test that fails if {@code @EnableScheduling} is dropped or the
	 * tick stops being wired — a failure whose only other symptom is an app that quietly never comes
	 * back to anyone.
	 */
	@Test
	void answersTheProbeWithoutAuthentication() {
		anonymous()
				.when()
				.get("/actuator/health/liveness")
				.then()
				.statusCode(200)
				.body("status", equalTo("UP"));
	}

	@Test
	void watchesTheSchedulerFromLivenessAndTheDatabaseOnlyFromReadiness() {
		assertThat(groups.get("liveness").isMember("proposalScheduler"))
				.as("a scheduler thread that died while the web server kept answering is invisible "
						+ "from every other angle — the symptom is the app doing nothing")
				.isTrue();
		assertThat(groups.get("liveness").isMember("db")).isFalse();
		assertThat(groups.get("readiness").isMember("db")).isTrue();
	}

	/**
	 * The groups above decide what Fly's probe sees; they decide nothing about the aggregate. Plain
	 * {@code /actuator/health} is exposed and permitAll — it has to be, so the liveness path reaches
	 * the probe through the filter chain — and it runs <em>every</em> registered contributor no matter
	 * which group names it. So a contributor here is not a diagnostic: it is work any anonymous
	 * request can make the app do, once per hit, for as long as anyone cares to ask.
	 *
	 * <p>The mail starter registers one that opens a connection and AUTHs against smtp.resend.com.
	 * Beside {@code db}'s {@code SELECT 1} that is a single unauthenticated URL holding Neon awake
	 * around the clock — the exact 24/7 wake-up the in-memory schedule exists to avoid, arriving
	 * through the door left open for the health check rather than through the tick.
	 */
	@Test
	void keepsTheMailCheckOffTheEndpointAnyoneCanPoll() {
		assertThat(contributors.getContributor("mail"))
				.as("an SMTP handshake per anonymous request, and a Neon wake-up beside it")
				.isNull();
		assertThat(contributors.getContributor("proposalScheduler")).isNotNull();
	}
}
