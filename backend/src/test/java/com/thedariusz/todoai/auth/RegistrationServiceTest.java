package com.thedariusz.todoai.auth;

import java.sql.SQLException;

import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class RegistrationServiceTest {

	private final UserRepository users = mock(UserRepository.class);

	private final AiMemoryRepository memories = mock(AiMemoryRepository.class);

	private final PasswordEncoder passwordEncoder = mock(PasswordEncoder.class);

	private final ApplicationEventPublisher events = mock(ApplicationEventPublisher.class);

	private final RegistrationService service =
			new RegistrationService(users, memories, passwordEncoder, events);

	@Test
	void translatesTheDatabaseUniqueConstraintRaceToDuplicateEmail() {
		when(passwordEncoder.encode("correct-horse")).thenReturn("{bcrypt}$2a$10$hash");
		when(users.saveAndFlush(any(User.class))).thenThrow(violationOf("app_user_email_key"));

		assertThatThrownBy(() -> service.register("alice@example.com", "correct-horse"))
				.isInstanceOf(EmailAlreadyRegisteredException.class)
				.hasCauseInstanceOf(DataIntegrityViolationException.class);
		verifyNoInteractions(memories, events);
	}

	/**
	 * Only the email constraint means "already registered". Any other integrity violation is a bug on
	 * our side, and reporting it as 409 would tell a first-time visitor their address is taken and
	 * send them to a login that cannot possibly work.
	 */
	@Test
	void doesNotDisguiseAnUnrelatedIntegrityViolationAsADuplicateEmail() {
		when(passwordEncoder.encode("correct-horse")).thenReturn("{bcrypt}$2a$10$hash");
		when(users.saveAndFlush(any(User.class))).thenThrow(violationOf("app_user_password_hash_check"));

		assertThatThrownBy(() -> service.register("alice@example.com", "correct-horse"))
				.isInstanceOf(DataIntegrityViolationException.class)
				.isNotInstanceOf(EmailAlreadyRegisteredException.class);
		verifyNoInteractions(memories, events);
	}

	/** The shape Spring hands back for a Postgres constraint breach: Hibernate's own as the cause. */
	private static DataIntegrityViolationException violationOf(String constraintName) {
		return new DataIntegrityViolationException("could not execute statement",
				new ConstraintViolationException("constraint violated", new SQLException("23505"), constraintName));
	}
}
