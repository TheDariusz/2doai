package com.thedariusz.todoai.auth;

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
 * <p>Bad credentials are deliberately absent: {@code ProblemDetailsSecurityHandler} answers those
 * from inside the filter chain, before any MVC handler runs, and keeps the Problem JSON identical
 * for "no such email" and "wrong password" (no user enumeration).
 */
@RestControllerAdvice
class ApiExceptionHandler extends ResponseEntityExceptionHandler {

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
		body.setTitle("Unprocessable Entity");
		return handleExceptionInternal(ex, body, headers, HttpStatus.UNPROCESSABLE_ENTITY, request);
	}

	@ExceptionHandler(EmailAlreadyRegisteredException.class)
	ProblemDetail handleEmailAlreadyRegistered(EmailAlreadyRegisteredException ex) {
		ProblemDetail body = ProblemDetail.forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
		body.setTitle("Conflict");
		return body;
	}
}
