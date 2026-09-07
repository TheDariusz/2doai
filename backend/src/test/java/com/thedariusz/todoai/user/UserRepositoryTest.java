package com.thedariusz.todoai.user;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.thedariusz.todoai.TestcontainersConfiguration;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Round-trips the {@link User} aggregate against a real Postgres (Testcontainers) with the
 * {@code V4} migration applied — proving the mapping validates against the migrated schema
 * ({@code ddl-auto=validate}), the UUIDv7 + audit-column conventions populate, the
 * case-insensitive {@code findByEmail} lookup works, and the
 * {@code app_user.email} UNIQUE constraint is enforced.
 */
@Import(TestcontainersConfiguration.class)
@SpringBootTest
class UserRepositoryTest {

	@Autowired
	UserRepository users;

	@Autowired
	JdbcTemplate jdbc;

	@Test
	void persistsAndFindsByEmail() {
		users.saveAndFlush(new User(Email.of("Alice@Example.com"), "{bcrypt}$2a$10$hash", AppLanguage.EN));

		User reloaded = users.findByEmail("alice@example.com").orElseThrow();

		assertThat(reloaded.getId()).isNotNull();
		assertThat(reloaded.getId().version()).isEqualTo(7);
		assertThat(reloaded.getEmail()).isEqualTo("alice@example.com");
		assertThat(reloaded.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$hash");
		assertThat(reloaded.getLanguage()).isEqualTo(AppLanguage.EN);
		assertThat(reloaded.getCreatedAt()).isNotNull();
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	@Test
	void enforcesUniqueEmail() {
		users.saveAndFlush(new User(Email.of("dup@example.com"), "{bcrypt}$2a$10$one", AppLanguage.PL));

		assertThatThrownBy(() -> users.saveAndFlush(
				new User(Email.of("dup@example.com"), "{bcrypt}$2a$10$two", AppLanguage.PL)))
				.isInstanceOf(DataIntegrityViolationException.class);
	}

	/**
	 * FR-002 — the switch moves one column and nothing else. A targeted update rather than a
	 * {@code save} of a loaded {@link User}, for the reason spelled out on
	 * {@code UserRepository.scheduleNextProposalAt}: an {@code update} cannot resurrect an account
	 * deleted in the meantime, and {@code User} keeps having no setters at all.
	 */
	@Test
	void movesTheLanguageWithoutRewritingTheRestOfTheRow() {
		User user = users.saveAndFlush(
				new User(Email.of("switcher@example.com"), "{bcrypt}$2a$10$hash", AppLanguage.PL));
		OffsetDateTime now = OffsetDateTime.now();

		assertThat(users.updateLanguage(user.getId(), AppLanguage.EN, now)).isEqualTo(1);

		User reloaded = users.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getLanguage()).isEqualTo(AppLanguage.EN);
		assertThat(reloaded.getEmail()).isEqualTo("switcher@example.com");
		assertThat(reloaded.getPasswordHash()).isEqualTo("{bcrypt}$2a$10$hash");
		assertThat(reloaded.getUpdatedAt()).isNotNull();
	}

	/** An account deleted between reading the principal and writing the column matches nothing. */
	@Test
	void reportsNoRowUpdatedForAnAccountThatNoLongerExists() {
		assertThat(users.updateLanguage(UUID.randomUUID(), AppLanguage.EN, OffsetDateTime.now())).isZero();
	}

	/**
	 * FR-004 — the shape of every account that existed before this change: the column is simply
	 * absent, because V10 adds it without a {@code DEFAULT} and without rewriting a single row. Null
	 * means "never chosen", which reads as Polish. Written here with raw SQL because the constructor
	 * cannot produce this state and never should — only a pre-migration row can.
	 */
	@Test
	void readsAnAccountThatNeverChoseALanguageAsPolish() {
		User user = users.saveAndFlush(
				new User(Email.of("legacy@example.com"), "{bcrypt}$2a$10$hash", AppLanguage.EN));
		jdbc.update("update app_user set preferred_language = null where id = ?", user.getId());

		assertThat(users.findById(user.getId()).orElseThrow().getLanguage()).isEqualTo(AppLanguage.PL);
	}
}
