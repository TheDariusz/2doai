package com.thedariusz.todoai.security;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.AuthenticationServiceException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.stereotype.Component;
import tools.jackson.databind.json.JsonMapper;

/**
 * Writes security-filter failures using the same RFC 9457 representation as MVC failures.
 *
 * <p>The 401 detail is deliberately generic so an unknown email and a wrong password remain
 * indistinguishable.
 *
 * <p><b>Not every failure here is the caller's fault.</b> {@code DaoAuthenticationProvider} wraps
 * anything thrown out of {@code loadUserByUsername} — a pool timeout, a Neon cold start that
 * overruns, a mapping error — in an {@link AuthenticationServiceException}, which is still an
 * {@link AuthenticationException} and so arrives on this same entry point. Reporting that as 401
 * would tell every user their password is wrong during a total database outage, while the logs stayed
 * silent. Infrastructure failures are separated out as 503 and logged with their cause; the cause is
 * never written to the response, where it would leak connection strings and SQL.
 */
@Component
public class ProblemDetailsSecurityHandler implements AuthenticationEntryPoint, AccessDeniedHandler {

	private static final Logger log = LoggerFactory.getLogger(ProblemDetailsSecurityHandler.class);

	private final JsonMapper jsonMapper;

	public ProblemDetailsSecurityHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {

		if (authException instanceof AuthenticationServiceException) {
			log.error("Authentication could not be completed for {} {} — treating as unavailable, not as bad credentials",
					request.getMethod(), request.getRequestURI(), authException);
			write(response, HttpStatus.SERVICE_UNAVAILABLE, "Authentication is temporarily unavailable");
			return;
		}
		if (authException instanceof BadCredentialsException) {
			log.warn("Credentials rejected for {} {}", request.getMethod(), request.getRequestURI());
		}
		else {
			log.debug("Unauthenticated request to {} {}: {}",
					request.getMethod(), request.getRequestURI(), authException.getClass().getSimpleName());
		}
		write(response, HttpStatus.UNAUTHORIZED, "Authentication is required or credentials are invalid");
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {

		log.warn("Access denied for {} {}: {}", request.getMethod(), request.getRequestURI(),
				accessDeniedException.getClass().getSimpleName());
		write(response, HttpStatus.FORBIDDEN, "The authenticated request is not allowed");
	}

	private void write(HttpServletResponse response, HttpStatus status, String detail) throws IOException {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, detail);
		body.setTitle(status.getReasonPhrase());
		response.setStatus(status.value());
		response.setContentType(MediaType.APPLICATION_PROBLEM_JSON_VALUE);
		jsonMapper.writeValue(response.getOutputStream(), body);
	}
}
