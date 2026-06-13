package com.thedariusz.todoai;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Bean;
import org.testcontainers.postgresql.PostgreSQLContainer;

/**
 * Gives {@code @SpringBootTest} a real Postgres. {@code @ServiceConnection}
 * auto-wires the container's JDBC coordinates into the context, so Flyway runs
 * and Hibernate validates against the same DB used in dev and production.
 * {@code @WebMvcTest} slices (e.g. PingControllerTest) load no datasource and
 * are unaffected.
 */
@TestConfiguration(proxyBeanMethods = false)
class TestcontainersConfiguration {

	@Bean
	@ServiceConnection
	PostgreSQLContainer postgresContainer() {
		// Testcontainers 2.x: PostgreSQLContainer is no longer generic (the
		// recursive <SELF> type parameter from 1.x was dropped).
		return new PostgreSQLContainer("postgres:18");
	}
}
