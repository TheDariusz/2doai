package com.thedariusz.todoai;

import com.zaxxer.hikari.HikariConfig;
import org.junit.jupiter.api.Test;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.bind.Bindable;
import org.springframework.boot.context.properties.bind.Binder;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Proves the committed {@code application.properties} keeps the Hikari pool settings that let a
 * metered, scale-to-zero Postgres (Neon) actually suspend — these are load-bearing NON-defaults,
 * not tuning noise. Mirrors {@code MemoryPropertiesTest}: binds the real keys onto the real
 * {@link HikariConfig}, so a typo or a well-meaning "simplify back to defaults" fails the build
 * rather than silently draining the monthly compute budget.
 *
 * <p>Why it matters: the Fly machine is always-on, so Hikari's default {@code minimumIdle} (=
 * {@code maximumPoolSize}) would hold connections open 24/7. Neon only autosuspends after ~5 min
 * with no activity, so an idle-but-open pool keeps the compute awake permanently — ~183 CU-h/month
 * against a 100 CU-h free-tier budget. Draining the pool is what makes the free tier viable.
 * See {@code context/foundation/lessons.md} → "Let a scale-to-zero database actually sleep".
 */
@SpringJUnitConfig(DataSourcePoolPropertiesTest.Config.class)
@TestPropertySource(locations = "classpath:application.properties")
class DataSourcePoolPropertiesTest {

	@Autowired
	Environment environment;

	@Test
	void poolDrainsToZeroSoNeonCanAutosuspend() {
		HikariConfig config = bindHikariConfig();

		// Default is -1 ("same as maximumPoolSize") — which would pin connections open forever.
		assertThat(config.getMinimumIdle()).isZero();
		// Default is 600_000 (10 min) — long enough to keep resetting Neon's ~5 min idle timer.
		assertThat(config.getIdleTimeout()).isEqualTo(30_000L);
	}

	@Test
	void keepaliveStaysDisabledSoIdleConnectionsNeverPingTheDatabase() {
		// A keepalive ping is indistinguishable from real traffic to Neon: enabling it would keep
		// the compute awake even with an empty pool.
		assertThat(bindHikariConfig().getKeepaliveTime()).isZero();
	}

	@Test
	void poolSizeStaysBoundedForA512MbMachine() {
		assertThat(bindHikariConfig().getMaximumPoolSize()).isEqualTo(5);
	}

	private HikariConfig bindHikariConfig() {
		HikariConfig config = new HikariConfig();
		Binder.get(environment).bind("spring.datasource.hikari", Bindable.ofInstance(config));
		return config;
	}

	@Configuration
	static class Config {
	}
}
