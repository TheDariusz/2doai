package com.thedariusz.todoai.auth;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import org.apache.commons.lang3.StringUtils;

/**
 * The link a code mail carries: {@code {base-url}/verify#email=<address>&code=<code>}. It opens the
 * SPA's code screen with both fields filled in. Nothing is spent on a GET: the SPA spends the code
 * itself, and does so without a press only in the browser that asked for that address's code
 * (DEV-62, {@code codeRequest.ts}). A mail scanner's browser, another device, or a link somebody else
 * sent gets the prefilled form and waits for the button.
 *
 * <p>Address and code ride in the <b>fragment</b>, which a browser never sends to a server and never
 * puts in a {@code Referer} — so neither reaches an access log, Cloudflare or Fly on the way in.
 *
 * <p>The path and the two names are a contract with the SPA, which routes the path and parses the
 * names. {@code VerificationApiTest.buildsTheMailLinkTheSpaRoutesAndParses} reads both sides.
 */
public final class SignInLink {

	/** The SPA route of the code screen. */
	public static final String PATH = "/verify";

	public static final String EMAIL_PARAM = "email";

	public static final String CODE_PARAM = "code";

	private SignInLink() {
	}

	/**
	 * @param baseUrl where the SPA lives ({@code app.mail.base-url}); a trailing slash is tolerated
	 * @param email the normalized address the code was sent to
	 * @param code the code itself, digits only
	 */
	public static String of(String baseUrl, String email, String code) {
		return StringUtils.removeEnd(baseUrl, "/") + PATH
				+ "#" + EMAIL_PARAM + "=" + URLEncoder.encode(email, StandardCharsets.UTF_8)
				+ "&" + CODE_PARAM + "=" + code;
	}
}
