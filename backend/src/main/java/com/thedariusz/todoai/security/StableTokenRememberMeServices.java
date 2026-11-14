package com.thedariusz.todoai.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Date;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.rememberme.InvalidCookieException;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenBasedRememberMeServices;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.security.web.authentication.rememberme.RememberMeAuthenticationException;

/**
 * Persistent remember-me whose token <b>never rotates</b>: an auto-login checks the series and the
 * value, records the use, and sends the same cookie back with a fresh lifetime.
 *
 * <p><b>Why not Spring's {@link PersistentTokenBasedRememberMeServices} as it ships.</b> It writes a
 * new value on every auto-login and reads any later request still carrying the old one as cookie
 * theft: it deletes every token the user has and raises {@code CookieTheftException}, which is thrown
 * above {@code ExceptionTranslationFilter} and so reaches the client as a 500. Two requests carrying
 * one cookie are normal here — two tabs restored together after a browser restart, or React
 * StrictMode bootstrapping twice in development — and the stock behaviour turns each of them into a
 * signed-out user and a server error. Rotation buys detection of a stolen cookie that is used
 * <em>after</em> its owner has used it again; with an {@code HttpOnly}, {@code SameSite=Strict},
 * {@code Secure} cookie on a single origin that is not the threat worth that price. Logout and
 * account deletion still revoke every token at once.
 *
 * <p><b>Every refusal is an anonymous request, never an error</b>: a malformed cookie, an unknown
 * series, a wrong value, an expired token or an unproved address all clear the cookie and let the
 * request continue without a user, which the authorization rules then answer with the usual 401. A
 * wrong value for a real series deletes nothing — it is a refused guess, not evidence against the
 * owner.
 *
 * <p>Two attributes the base class gets wrong for this application are set here instead:
 * {@code SameSite=Strict} on the set cookie (through the customizer) <em>and</em> on the cancel
 * cookie, which the base class builds without consulting the customizer; and {@code Secure} from the
 * same environment switch as the session cookie rather than from {@code request.isSecure()}.
 *
 * <p>The base class's {@code alwaysRemember} is on: the SPA signs in with a JSON body, which is not a
 * request parameter the base class could read a "remember me" choice from, and every sign-in is
 * meant to be remembered.
 */
public class StableTokenRememberMeServices extends PersistentTokenBasedRememberMeServices {

	/** The cookie name, part of the wire contract in {@code openapi.yaml}. */
	public static final String COOKIE_NAME = "remember-me";

	/** How long a remembered browser may go unused before it has to sign in again. */
	public static final Duration VALIDITY = Duration.ofDays(90);

	private static final String SAME_SITE = "Strict";

	private final PersistentTokenRepository tokens;

	private final boolean secureCookie;

	public StableTokenRememberMeServices(String key, UserDetailsService userDetailsService,
			PersistentTokenRepository tokens, boolean secureCookie) {
		super(key, userDetailsService, tokens);
		this.tokens = tokens;
		this.secureCookie = secureCookie;
		setCookieName(COOKIE_NAME);
		setAlwaysRemember(true);
		setTokenValiditySeconds(Math.toIntExact(VALIDITY.toSeconds()));
		setUseSecureCookie(secureCookie);
		setCookieCustomizer(cookie -> cookie.setAttribute("SameSite", SAME_SITE));
	}

	@Override
	protected UserDetails processAutoLoginCookie(String[] cookieTokens, HttpServletRequest request,
			HttpServletResponse response) {

		if (cookieTokens.length != 2) {
			throw new InvalidCookieException("The remember-me cookie does not hold a series and a value");
		}
		String series = cookieTokens[0];
		String presentedValue = cookieTokens[1];

		PersistentRememberMeToken token = tokens.getTokenForSeries(series);
		if (token == null) {
			throw new RememberMeAuthenticationException("No remember-me token for the presented series");
		}
		if (!MessageDigest.isEqual(bytes(presentedValue), bytes(token.getTokenValue()))) {
			throw new InvalidCookieException("The remember-me value does not match its series");
		}
		Date now = new Date();
		if (token.getDate().toInstant().plus(VALIDITY).isBefore(now.toInstant())) {
			throw new RememberMeAuthenticationException("The remember-me token has expired");
		}

		UserDetails user = getUserDetailsService().loadUserByUsername(token.getUsername());
		// Checked here as well as by the base class afterwards, so that an unproved account is refused
		// before the token is touched and before a renewed cookie is written onto the response.
		if (!user.isEnabled()) {
			throw new DisabledException("The account's address has not been proved");
		}

		tokens.updateToken(series, token.getTokenValue(), now);
		setCookie(new String[] { series, token.getTokenValue() }, getTokenValiditySeconds(), request, response);
		return user;
	}

	/**
	 * The base class's cancel cookie, with the attributes it leaves off. Written as a
	 * {@link ResponseCookie} header, which states {@code SameSite} natively; {@code Secure} follows
	 * the same environment switch as the cookie it cancels.
	 */
	@Override
	protected void cancelCookie(HttpServletRequest request, HttpServletResponse response) {
		ResponseCookie cookie = ResponseCookie.from(getCookieName(), "")
				.maxAge(0)
				.path(request.getContextPath().isEmpty() ? "/" : request.getContextPath())
				.secure(secureCookie)
				.httpOnly(true)
				.sameSite(SAME_SITE)
				.build();
		response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
	}

	private static byte[] bytes(String value) {
		return value.getBytes(StandardCharsets.UTF_8);
	}
}
