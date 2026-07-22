package com.thedariusz.todoai.security;

import java.io.IOException;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Forces Spring Security 6's <em>deferred</em> CSRF token to render on every request, so the
 * {@code XSRF-TOKEN} cookie is written even on the SPA's <b>unauthenticated bootstrap</b> call.
 *
 * <p>Why this is needed: SS6 defers CSRF-token generation until the token is read, and the
 * {@code GET /api/users/me} bootstrap endpoint is gated — an unauthenticated first load is rejected
 * with 401 by the authorization filter <em>before</em> the handler runs, so a "let the {@code /me}
 * handler read the token" approach never primes the cookie, and the first login {@code POST} would
 * then fail CSRF with 403. This filter is ordered <em>before</em> the authorization filter, so it
 * materializes the token (writing the cookie) on the way in; the cookie survives on the 401
 * response. Reading {@link CsrfToken#getToken()} is what triggers the {@code CookieCsrfTokenRepository}
 * to emit the cookie. This is the canonical SS6 single-page-app recipe.
 */
final class CsrfCookieFilter extends OncePerRequestFilter {

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
			throws ServletException, IOException {
		CsrfToken csrfToken = (CsrfToken) request.getAttribute(CsrfToken.class.getName());
		if (csrfToken != null) {
			// Rendering the (deferred) token value is the side effect that makes the repository
			// write the XSRF-TOKEN cookie onto the response.
			csrfToken.getToken();
		}
		filterChain.doFilter(request, response);
	}
}
