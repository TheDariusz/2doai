package com.thedariusz.todoai.session;

import com.thedariusz.todoai.auth.EmailVerificationService;
import com.thedariusz.todoai.auth.LoginRequest;
import com.thedariusz.todoai.auth.VerificationFailedException;
import com.thedariusz.todoai.security.AuthenticatedSession;
import com.thedariusz.todoai.user.CodePurpose;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.web.authentication.logout.LogoutHandler;
import org.springframework.security.web.authentication.session.SessionAuthenticationStrategy;
import org.springframework.transaction.CannotCreateTransactionException;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * How the code path on {@code POST /api/sessions} reports a failure. The password path gets this from
 * {@code DaoAuthenticationProvider}; the code path has no provider, so the controller does the same
 * translation by hand: a refused code is bad credentials (the generic 401), and anything the database
 * throws is a service failure (503) — telling every user their code is wrong during an outage would
 * be the lie {@code ProblemDetailsSecurityHandler} exists to prevent.
 */
class SessionControllerTest {

	private final EmailVerificationService verification = mock(EmailVerificationService.class);

	private final SessionController controller = new SessionController(mock(AuthenticationManager.class),
			mock(SessionAuthenticationStrategy.class), mock(LogoutHandler.class), mock(AuthenticatedSession.class),
			verification);

	private final LoginRequest byCode = new LoginRequest("ala@example.pl", null, "04231789");

	@Test
	void reportsARefusedCodeAsBadCredentials() {
		when(verification.redeem(any(), any(), any(CodePurpose.class))).thenThrow(new VerificationFailedException());

		assertThatThrownBy(this::signIn).isInstanceOf(BadCredentialsException.class);
	}

	@Test
	void reportsADatabaseFailureAsAServiceFailureNotAsBadCredentials() {
		when(verification.redeem(any(), any(), any(CodePurpose.class)))
				.thenThrow(new DataAccessResourceFailureException("pool exhausted"));

		assertThatThrownBy(this::signIn).isInstanceOf(AuthenticationServiceException.class);
	}

	@Test
	void reportsATransactionThatCouldNotStartAsAServiceFailure() {
		when(verification.redeem(any(), any(), any(CodePurpose.class)))
				.thenThrow(new CannotCreateTransactionException("no connection"));

		assertThatThrownBy(this::signIn).isInstanceOf(AuthenticationServiceException.class);
	}

	private void signIn() {
		controller.login(byCode, new MockHttpServletRequest(), new MockHttpServletResponse());
	}
}
