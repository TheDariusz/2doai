package com.thedariusz.todoai.auth;

import com.thedariusz.todoai.ai.memory.AiMemory;
import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Creates an account: the {@link User} and their {@link AiMemory} root in <b>one transaction</b>,
 * so the "every user has exactly one memory" invariant holds from t=0 and the {@code V5} FK on
 * {@code ai_memory.user_id} is satisfiable the moment the user exists.
 *
 * <p>Registration deliberately does <b>not</b> log the user in — {@code POST /api/sessions} is the
 * single session-creation path, so there is one place where a session can come into being.
 */
@Service
public class RegistrationService {

	private final UserRepository users;

	private final AiMemoryRepository memories;

	private final PasswordEncoder passwordEncoder;

	public RegistrationService(UserRepository users, AiMemoryRepository memories, PasswordEncoder passwordEncoder) {
		this.users = users;
		this.memories = memories;
		this.passwordEncoder = passwordEncoder;
	}

	@Transactional
	public User register(String rawEmail, String rawPassword) {
		Email email = Email.of(rawEmail);
		if (users.existsByEmail(email.value())) {
			throw new EmailAlreadyRegisteredException();
		}
		// The raw password stops here: the aggregate only ever receives the encoded hash.
		User user = users.save(new User(email, passwordEncoder.encode(rawPassword)));
		memories.save(new AiMemory(user.getId()));
		return user;
	}
}
