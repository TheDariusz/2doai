package com.thedariusz.todoai.auth;

import java.time.OffsetDateTime;
import java.util.Optional;

import com.thedariusz.todoai.ai.memory.AiMemory;
import com.thedariusz.todoai.ai.memory.AiMemoryRepository;
import com.thedariusz.todoai.user.AppLanguage;
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
 *
 * <p>It announces nothing, either. The account it creates is <b>inert</b> until the address is
 * proved (DEV-51): {@code UserVerified} is what the rest of the app listens for, and it is published
 * by verification, not from here. Anything started at sign-up would be started for an address nobody
 * has consented with.
 *
 * <p>It does not send the code either — {@link EmailVerificationService#issue} does, once this
 * transaction has committed. SMTP inside it would pin a database connection across two ten-second
 * timeouts, and a provider outage would roll back an account that was otherwise perfectly created.
 */
@Service
public class RegistrationService {

	private static final Logger log = LoggerFactory.getLogger(RegistrationService.class);

	/** Postgres' generated name for the inline {@code UNIQUE} on {@code app_user.email} (V4). */
	private static final String EMAIL_CONSTRAINT = "app_user_email";

	private final UserRepository users;

	private final AiMemoryRepository memories;

	private final PasswordEncoder passwordEncoder;

	public RegistrationService(UserRepository users, AiMemoryRepository memories,
			PasswordEncoder passwordEncoder) {
		this.users = users;
		this.memories = memories;
		this.passwordEncoder = passwordEncoder;
	}

	/**
	 * @param language the language the sign-up screen was shown in (FR-003). It is a parameter rather
	 *         than a field on {@code RegisterRequest} because the SPA already advertises what it is
	 *         rendering on every call: the sign-up screen's language <em>is</em> the
	 *         {@code Accept-Language} the sign-up request carried, so asking for it twice would create
	 *         a second answer that can disagree with the first.
	 * @return the account to send a code to — freshly created, or the unverified one just taken over
	 * @throws EmailAlreadyRegisteredException only when the address belongs to a <em>verified</em>
	 *         account
	 */
	@Transactional
	public User register(String rawEmail, String rawPassword, AppLanguage language) {
		Email email = Email.of(rawEmail);
		String passwordHash = passwordEncoder.encode(rawPassword);

		// One SELECT per registration, which the constraint alone used to save (DEV-51 spends it): a
		// duplicate is no longer simply a 409, so the row behind it has to be in hand to decide. The
		// UNIQUE index is still the authority — the lookup can lose a concurrent race, the insert below
		// cannot, and it reports the loss as the same 409.
		Optional<User> existing = users.findByEmail(email.value());
		if (existing.isPresent()) {
			return takeOver(existing.get(), passwordHash, language);
		}

		User user = new User(email, passwordHash, language);
		try {
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
	 * Signing up again with an address whose owner never proved it (DEV-51). Nobody is known to be
	 * behind such an account and it holds nothing worth protecting, so the second sign-up takes it
	 * over — which is what keeps a typo'd or abandoned attempt from blocking the real owner with a
	 * 409 they have no way to resolve. The memory root is not re-created: the first sign-up made it,
	 * and this is the same account.
	 *
	 * <p>The verified guard lives in {@code replaceUnverifiedAccount}'s {@code where} clause, not
	 * here — see its javadoc for why a caller must not be the one holding it.
	 */
	private User takeOver(User account, String passwordHash, AppLanguage language) {
		if (users.replaceUnverifiedAccount(account.getId(), passwordHash, language,
				OffsetDateTime.now()) == 0) {
			log.info("Registration rejected: email already taken");
			throw new EmailAlreadyRegisteredException();
		}
		log.info("Account {} was signed up for again before it was ever verified", account.getId());
		return account;
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
