package com.thedariusz.todoai;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.boot.webmvc.error.ErrorController;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Renders container-level errors as RFC 9457, the same representation
 * {@code ApiExceptionHandler} and {@code ProblemDetailsSecurityHandler} use.
 *
 * <p>Boot's {@code BasicErrorController} answers {@code /error} with its own
 * {@code timestamp/status/error/path} object under {@code application/json}. That is the one shape
 * the SPA cannot anticipate, arriving on exactly the responses it least expects — an unhandled 500,
 * or a 404 on a path with no mapping. Defining an {@link ErrorController} bean makes the
 * auto-configured one back off.
 *
 * <p>The detail is deliberately fixed. Everything useful about a 500 — the exception, its cause, the
 * stack — belongs in the log, not in a response body a stranger can read; the {@code server.error.*}
 * include-flags are left at their {@code never} defaults for the same reason.
 */
@RestController
class ProblemErrorController implements ErrorController {

	@RequestMapping("${server.error.path:/error}")
	ResponseEntity<ProblemDetail> handleError(HttpServletRequest request) {
		HttpStatus status = resolveStatus(request);
		ProblemDetail body = ProblemDetail.forStatusAndDetail(status, "The request could not be completed");
		body.setTitle(status.getReasonPhrase());
		return ResponseEntity.status(status).contentType(MediaType.APPLICATION_PROBLEM_JSON).body(body);
	}

	/** The status the container recorded before forwarding here; anything unreadable is our fault. */
	private static HttpStatus resolveStatus(HttpServletRequest request) {
		Object code = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
		if (code instanceof Integer statusCode) {
			HttpStatus resolved = HttpStatus.resolve(statusCode);
			if (resolved != null) {
				return resolved;
			}
		}
		return HttpStatus.INTERNAL_SERVER_ERROR;
	}
}
