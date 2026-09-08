package com.thedariusz.todoai.security;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.HttpSessionSecurityContextRepository;

import java.util.UUID;

import com.thedariusz.todoai.user.AppLanguage;

import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The one path {@code UserApiTest} cannot reach, because the filter chain answers 401 long before
 * a handler runs: {@link AuthenticatedSession#replacePrincipal} called with nothing on the holder.
 */
class AuthenticatedSessionTest {

	private final AuthenticatedSession session =
				new AuthenticatedSession(new HttpSessionSecurityContextRepository());

	@AfterEach
	void clearContext() {
		SecurityContextHolder.clearContext();
	}

	/**
	 * It reads the current authentication to carry credentials, authorities and details across. With
	 * no authentication to read, the honest answer is the 401 an {@code AuthenticationException}
	 * renders as — not the 500 a bare dereference would have produced.
	 */
	@Test
	void refusesToReAuthenticateASessionThatWasNeverAuthenticated() {
		UserPrincipal principal = new UserPrincipal(
				UUID.randomUUID(), "ala@example.pl", "{bcrypt}$2a$10$hash", AppLanguage.EN, true);

		assertThatThrownBy(() -> session.replacePrincipal(
				principal, new MockHttpServletRequest(), new MockHttpServletResponse()))
				.isInstanceOf(AuthenticationCredentialsNotFoundException.class);
	}
}
