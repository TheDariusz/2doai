package com.thedariusz.todoai.observability;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

import ch.qos.logback.classic.LoggerContext;
import ch.qos.logback.classic.joran.JoranConfigurator;
import ch.qos.logback.core.joran.spi.JoranException;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Pins the wire shape of what 2doai pushes to Loki, by running the production appender fragment
 * ({@code logback-loki.xml}) against a local HTTP server standing in for Grafana Cloud. The alert
 * rules in thedariusz-ops key on exactly these labels and JSON keys, so a drift here is a silent
 * alert, not a failing build anywhere else.
 */
class LokiLogLineTest {

	private record Push(String authorization, String body) {
	}

	private static final JsonMapper JSON = JsonMapper.builder().build();

	private final BlockingQueue<Push> pushes = new LinkedBlockingQueue<>();

	private HttpServer loki;

	private int status = 204;

	@BeforeEach
	void startFakeLoki() throws IOException {
		loki = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
		loki.createContext("/loki/api/v1/push", exchange -> {
			pushes.add(new Push(exchange.getRequestHeaders().getFirst("Authorization"),
					new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8)));
			exchange.sendResponseHeaders(status, -1);
			exchange.close();
		});
		loki.start();
	}

	@AfterEach
	void stopFakeLoki() {
		loki.stop(0);
	}

	@Test
	void structuredFieldsComeOutAsTopLevelJsonKeysUnderTheContractLabels() throws Exception {
		UUID id = UUID.randomUUID();
		LoggerContext context = configure("tenant-1", "secret-1");

		context.getLogger("com.thedariusz.todoai.observability.Probe")
			.atInfo()
			.addKeyValue("event", "user_registered")
			.addKeyValue("user_id", id)
			.log("user registered");

		Push push = pushes.poll(10, TimeUnit.SECONDS);
		context.stop();
		assertThat(push).as("a push reached the fake Loki").isNotNull();
		assertThat(push.authorization()).isEqualTo("Basic "
				+ Base64.getEncoder().encodeToString("tenant-1:secret-1".getBytes(StandardCharsets.UTF_8)));

		JsonNode stream = JSON.readTree(push.body()).get("streams").get(0);
		assertThat(JSON.convertValue(stream.get("stream"), java.util.Map.class))
			.containsExactlyInAnyOrderEntriesOf(java.util.Map.of("service", "2doai", "env", "prod", "level", "info"));

		JsonNode value = stream.get("values").get(0);
		assertThat(value.size()).as("no structured metadata element").isEqualTo(2);
		JsonNode line = JSON.readTree(value.get(1).asString());
		assertThat(line.get("msg").asString()).isEqualTo("user registered");
		assertThat(line.get("logger").asString()).isEqualTo("com.thedariusz.todoai.observability.Probe");
		assertThat(line.get("level").asString()).isEqualTo("info");
		assertThat(line.get("event").asString()).isEqualTo("user_registered");
		assertThat(line.get("user_id").asString()).isEqualTo(id.toString());
		assertThat(line.has("ts")).isTrue();
		assertThat(fieldNames(line)).containsExactlyInAnyOrder("ts", "logger", "level", "msg", "event", "user_id");
	}

	@Test
	void anExceptionTravelsAsErrAndTheLevelLabelIsLowercase() throws Exception {
		LoggerContext context = configure("tenant-1", "secret-1");

		context.getLogger("com.thedariusz.todoai.observability.Probe")
			.error("fire failed", new IllegalStateException("boom"));

		Push push = pushes.poll(10, TimeUnit.SECONDS);
		context.stop();
		assertThat(push).isNotNull();
		JsonNode stream = JSON.readTree(push.body()).get("streams").get(0);
		assertThat(stream.get("stream").get("level").asString()).isEqualTo("error");
		JsonNode line = JSON.readTree(stream.get("values").get(0).get(1).asString());
		assertThat(line.get("level").asString()).isEqualTo("error");
		assertThat(line.get("err").asString()).contains("IllegalStateException: boom");
	}

	@Test
	void anAddressInAMessageOrACauseLeavesOnlyItsDomain() throws Exception {
		// The app's own lines name only a domain, but a third-party cause does not ask: Spring's
		// MailSendException quotes the SMTP reply, and that quotes the recipient.
		LoggerContext context = configure("tenant-1", "secret-1");

		context.getLogger("com.thedariusz.todoai.observability.Probe")
			.error("could not mail jan.kowalski@example.com", new IllegalStateException("send failed",
					new IllegalStateException("550 5.1.1 <jan.kowalski+2do@example.com>: Recipient address rejected")));

		Push push = pushes.poll(10, TimeUnit.SECONDS);
		context.stop();
		assertThat(push).isNotNull();
		assertThat(push.body()).doesNotContain("jan.kowalski");
		JsonNode line = JSON.readTree(JSON.readTree(push.body()).get("streams").get(0).get("values").get(0).get(1).asString());
		assertThat(line.get("msg").asString()).isEqualTo("could not mail <redacted>@example.com");
		assertThat(line.get("err").asString()).contains("<<redacted>@example.com>: Recipient address rejected");
	}

	@Test
	void aLineReachesLokiWithinSecondsWithoutWaitingForShutdown() throws Exception {
		// The shared contract wants lines in Loki "within seconds"; loki4j's default batch timeout is a
		// minute, which is also how much a hard-killed machine would take with it. Every test here
		// waits for the push before stopping, because loki4j's drain-on-stop can close its HTTP client
		// before a queued request is sent: that race made these tests flaky under a loaded build.
		LoggerContext context = configure("tenant-1", "secret-1");

		context.getLogger("com.thedariusz.todoai.observability.Probe").info("prompt");

		assertThat(pushes.poll(10, TimeUnit.SECONDS)).as("pushed before the context stopped").isNotNull();
		context.stop();
	}

	@Test
	void aMissingTokenCostsTheLogLinesNotTheApp() throws Exception {
		// The first deploy can land before `fly secrets set LOKI_TOKEN`: Loki answers 401, and the
		// only consequence may be lost lines — no exception reaches the logging call site.
		status = 401;
		LoggerContext context = configure("tenant-1", null);

		context.getLogger("com.thedariusz.todoai.observability.Probe").info("still alive");
		assertThat(context.getLogger("ROOT").getAppender("LOKI").isStarted()).isTrue();

		Push push = pushes.poll(10, TimeUnit.SECONDS);
		context.stop();
		assertThat(push).as("the push was attempted").isNotNull();
	}

	private LoggerContext configure(String user, String token) throws JoranException {
		LoggerContext context = new LoggerContext();
		context.putProperty("LOKI_PUSH_URL", "http://localhost:" + loki.getAddress().getPort() + "/loki/api/v1/push");
		context.putProperty("LOKI_USER", user);
		if (token != null) {
			context.putProperty("LOKI_TOKEN", token);
		}
		JoranConfigurator configurator = new JoranConfigurator();
		configurator.setContext(context);
		configurator.doConfigure(getClass().getResource("/observability/loki-appender-under-test.xml"));
		return context;
	}

	private static List<String> fieldNames(JsonNode node) {
		return node.propertyNames().stream().toList();
	}

}
