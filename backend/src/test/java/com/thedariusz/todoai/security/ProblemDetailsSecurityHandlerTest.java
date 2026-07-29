package com.thedariusz.todoai.security;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.InternalAuthenticationServiceException;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The handler has to tell two very different failures apart. A wrong password is a 401 and the
 * user's problem; a {@code DataSource} that will not answer is a 503 and <em>ours</em>. Spring
 * funnels both through {@link org.springframework.security.web.AuthenticationEntryPoint} — {@code
 * DaoAuthenticationProvider} wraps anything thrown out of {@code loadUserByUsername} in an
 * {@link InternalAuthenticationServiceException}, which is still an {@code AuthenticationException} —
 * so without this split a Neon outage reaches every user as "your credentials are invalid".
 */
class ProblemDetailsSecurityHandlerTest {

	private final ProblemDetailsSecurityHandler handler =
			new ProblemDetailsSecurityHandler(JsonMapper.builder().build());

	@Test
	void rendersBadCredentialsAsAGeneric401() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.commence(new MockHttpServletRequest(), response, new BadCredentialsException("nope"));

		assertThat(response.getStatus()).isEqualTo(401);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
		assertThat(response.getContentAsString())
				.contains("Authentication is required or credentials are invalid")
				.doesNotContain("nope");
	}

	@Test
	void rendersAnInfrastructureFailureAsA503RatherThanA401() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.commence(new MockHttpServletRequest(), response,
				new InternalAuthenticationServiceException("connection timed out", new RuntimeException()));

		assertThat(response.getStatus()).isEqualTo(503);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
	}

	/** The detail must never echo the underlying cause — that is where SQL text and hostnames live. */
	@Test
	void neverLeaksTheUnderlyingCauseToTheClient() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.commence(new MockHttpServletRequest(), response,
				new InternalAuthenticationServiceException(
						"FATAL: password authentication failed for user \"neon_owner\"", new RuntimeException()));

		assertThat(response.getContentAsString()).doesNotContain("neon_owner");
	}

	@Test
	void rendersAccessDeniedAsA403() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		handler.handle(new MockHttpServletRequest(), response, new AccessDeniedException("denied"));

		assertThat(response.getStatus()).isEqualTo(403);
		assertThat(response.getContentType()).isEqualTo("application/problem+json");
	}
}
