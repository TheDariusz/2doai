package com.thedariusz.todoai.user;

import com.thedariusz.todoai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trips the {@link User} aggregate against a real Postgres (Testcontainers) with the
 * {@code V4} migration applied — proving the mapping validates against the migrated schema
 * ({@code ddl-auto=validate}), the UUIDv7 + audit-column conventions populate, the
 * case-insensitive {@code findByEmail}/{@code existsByEmail} lookups work, and the
 * {@code app_user.email} UNIQUE constraint is enforced.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserRepositoryTest {

	@Autowired
	UserRepository users;

	@Test
	void persistsAndFindsByEmail() {
		users.saveAndFlush(new User(Email.of("Alice@Example.com"), "{bcrypt}$2a$10$hash"));

		User reloaded = users.findByEmail("alice@example.com").orElseThrow();

		assertThat(reloaded.getId()).isNotNull();
		assertThat(reloaded.getId().version()).isEqualTo(7);
		assertThat(reloaded.getEmail()).isEqualTo("alice@example.com");
		assertThat(reloaded.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$hash");
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void existsByEmailReflectsPersistedRows() {
		users.saveAndFlush(new User(Email.of("present@example.com"), "{bcrypt}$2a$10$hash"));

		assertThat(users.existsByEmail("present@example.com")).isTrue();
		assertThat(users.existsByEmail("absent@example.com")).isFalse();
	}

	@Test
	void enforcesUniqueEmail() {
		users.saveAndFlush(new User(Email.of("dup@example.com"), "{bcrypt}$2a$10$one"));

		assertThatThrownBy(() -> users.saveAndFlush(new User(Email.of("dup@example.com"), "{bcrypt}$2a$10$two")))
				.isInstanceOf(DataIntegrityViolationException.class);
	}
}
