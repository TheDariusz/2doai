package com.thedariusz.todoai.auth;

import java.net.URI;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

/**
 * Maps failures to <b>Problem JSON</b> (RFC 9457, {@code application/problem+json}) — extending
 * {@link ResponseEntityExceptionHandler} means Spring's own MVC exceptions already come out as
 * {@link ProblemDetail}; only the two project-specific mappings are written here.
 *
 * <p>Bad credentials are deliberately absent. They are raised <em>inside</em> a controller but escape
 * MVC entirely: {@code ExceptionTranslationFilter} catches the {@code AuthenticationException} on the
 * way out and hands it to {@code ProblemDetailsSecurityHandler}, so no {@code @ExceptionHandler} here
 * would ever see one. That handler keeps the Problem JSON identical for "no such email" and "wrong
 * password" (no user enumeration).
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

	/** Documented in openapi.yaml; this class is its only producer. */
	private static final URI RE_AUTH_FAILED = URI.create("urn:2doai:problem:re-auth-failed");

	/**
	 * Validation → <b>422</b>, not Spring's default 400: the request parsed fine, its content failed
	 * the contract's constraints. The detail stays generic — field-level messages would echo the
	 * submitted email back to an unauthenticated caller.
	 */
	@Override
	protected ResponseEntity<Object> handleMethodArgumentNotValid(
			MethodArgumentNotValidException ex, HttpHeaders headers, HttpStatusCode status, WebRequest request) {

		ProblemDetail body = ProblemDetail.forStatusAndDetail(
				HttpStatus.UNPROCESSABLE_ENTITY, "The request failed validation");
		// The one title worth spelling out: Spring's fallback for 422 is now RFC 9110's
		// "Unprocessable Content", and openapi.yaml documents the older "Unprocessable Entity".
		body.setTitle("Unprocessable Entity");
		return handleExceptionInternal(ex, body, headers, HttpStatus.UNPROCESSABLE_ENTITY, request);
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
		return ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
	}

	/**
	 * The other 403 reachable on {@code DELETE /api/users/me} is a CSRF denial from
	 * {@code ProblemDetailsSecurityHandler}, and with Spring's defaults the two are identical on the
	 * wire — same status, same {@code Forbidden} title, and no {@code type} member at all (Boot 4
	 * omits it for an unset {@link ProblemDetail} rather than emitting {@code about:blank}) —
	 * leaving only the {@code detail} prose, which RFC 9457 says clients must not parse. The URN is
	 * the discriminator that lets the SPA say "wrong password" instead of hedging across both causes.
	 */
	@ExceptionHandler(ReAuthenticationFailedException.class)
	ProblemDetail handleReAuthenticationFailed(ReAuthenticationFailedException ex) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
		body.setType(RE_AUTH_FAILED);
		body.setTitle("Re-authentication failed");
		return body;
	}

	/**
	 * The single place every handled failure passes through, including the twenty exception types
	 * {@link ResponseEntityExceptionHandler} maps for us — three of which are genuine 500-class server
	 * bugs it would otherwise render silently. Without this a Jackson serialization failure on a
	 * response returns a bare 500 and writes nothing to the log.
	 */
	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode status, WebRequest request) {

		if (status.is5xxServerError()) {
			logger.error("Request to " + request.getDescription(false) + " failed with " + status, ex);
		}
		else {
			logger.warn("Request to " + request.getDescription(false) + " rejected with " + status
					+ ": " + ex.getClass().getSimpleName());
		}
		return super.handleExceptionInternal(ex, body, headers, status, request);
	}
}
