package com.thedariusz.todoai.auth;

import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.junit.jupiter.api.Test;
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

	private final RegistrationService service = new RegistrationService(users, memories, passwordEncoder);

	@Test
	void translatesTheDatabaseUniqueConstraintRaceToDuplicateEmail() {
		when(passwordEncoder.encode("correct-horse")).thenReturn("{bcrypt}$2a$10$hash");
		when(users.saveAndFlush(any(User.class)))
				.thenThrow(new DataIntegrityViolationException("app_user_email_key"));

		assertThatThrownBy(() -> service.register("alice@example.com", "correct-horse"))
				.isInstanceOf(EmailAlreadyRegisteredException.class)
				.hasCauseInstanceOf(DataIntegrityViolationException.class);
		verifyNoInteractions(memories);
	}
}
