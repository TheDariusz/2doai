package com.thedariusz.todoai.auth;

import com.thedariusz.todoai.ai.memory.AiMemory;
import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.exception.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
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

	private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

	/** Postgres' generated name for the inline {@code UNIQUE} on {@code app_user.email} (V4). */
	private static final String EMAIL_CONSTRAINT = "app_user_email";

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
		User user = new User(Email.of(rawEmail), passwordEncoder.encode(rawPassword));
		try {
			// Flush while still inside this service so the UNIQUE(email) constraint is translated to
			// the API's 409. The constraint is the *only* duplicate check: an application-level
			// pre-check would cost a SELECT on every registration and still lose a concurrent race.
			user = users.saveAndFlush(user);
		}
		catch (DataIntegrityViolationException ex) {
			if (!isEmailUniquenessViolation(ex)) {
				log.error("Registration failed on an unexpected integrity violation", ex);
				throw ex;
			}
			log.info("Registration rejected: email already taken");
			throw new EmailAlreadyRegisteredException(ex);
		}
		memories.save(new AiMemory(user.getId()));
		return user;
	}

	/**
	 * Only the email index means "already registered". {@code DataIntegrityViolationException} covers a
	 * whole family of failures — a null column, an overflowed width, any constraint a later migration
	 * adds — and reporting those as 409 would tell a first-time visitor their address is taken.
	 */
	private static boolean isEmailUniquenessViolation(DataIntegrityViolationException ex) {
		return ex.getCause() instanceof ConstraintViolationException violation
				&& StringUtils.containsIgnoreCase(violation.getConstraintName(), EMAIL_CONSTRAINT);
	}
}
