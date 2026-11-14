package com.thedariusz.todoai.auth;

import org.apache.commons.lang3.StringUtils;

import org.springframework.web.util.HtmlUtils;

/**
 * The HTML shape of the code mail, shared by every locale (DEV-62). The wording is each locale's own
 * ({@link VerificationEmailPl}, {@link VerificationEmailEn}); this class owns only the layout, so a
 * second language is a second {@link Copy}, never a second copy of the markup.
 *
 * <p>Built the way mail clients force: one table-based column, every style inline, no web fonts, no
 * script, no external CSS. Gmail strips {@code <style>} in some views and Outlook renders with Word,
 * so anything subtler degrades. The logo is the PWA icon the app already serves, so the mail needs
 * no asset of its own.
 *
 * <p>Every inserted value is escaped, the link included: its fragment carries an {@code &}, which in an
 * attribute must be {@code &amp;}.
 */
final class VerificationEmailLayout {

	/** The PWA icon, served by the SPA from its {@code public/} folder. */
	static final String LOGO_PATH = "/icon-192.png";

	private static final String ACCENT = "#863bff";

	private VerificationEmailLayout() {
	}

	/** How long a code lives, in the whole minutes every locale's copy states it in. */
	static long codeMinutes() {
		return EmailVerificationService.CODE_VALIDITY.toMinutes();
	}

	/**
	 * The words one locale fills the layout with.
	 *
	 * @param lang the document's {@code lang} attribute
	 * @param preheader the hidden line a client shows next to the subject in the inbox
	 * @param heading the one line the eye lands on
	 * @param intro what the button does
	 * @param button the button's label
	 * @param codeLabel what introduces the code, for whoever types it instead
	 * @param validity how long the code lasts and that it works once
	 * @param ignore what to do with a message one did not ask for
	 * @param tagline one line on what the app is, in the footer
	 */
	record Copy(String lang, String preheader, String heading, String intro, String button, String codeLabel,
			String validity, String ignore, String tagline) {
	}

	static String render(Copy copy, String code, String link, String baseUrl) {
		String logo = StringUtils.removeEnd(baseUrl, "/") + LOGO_PATH;
		return """
				<!doctype html>
				<html lang="%1$s">
				<head>
				<meta charset="utf-8">
				<meta name="viewport" content="width=device-width, initial-scale=1">
				<meta name="color-scheme" content="light">
				<meta name="supported-color-schemes" content="light">
				<title>%3$s</title>
				</head>
				<body style="margin:0;padding:0;background:#f4f2fa;">
				<div style="display:none;max-height:0;overflow:hidden;opacity:0;">%2$s</div>
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="background:#f4f2fa;">
				<tr><td align="center" style="padding:32px 16px;">
				<table role="presentation" width="100%%" cellpadding="0" cellspacing="0" border="0" style="max-width:480px;">
				<tr><td style="padding:0 8px 20px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;">
				<img src="%10$s" width="36" height="36" alt="" style="vertical-align:middle;border:0;border-radius:8px;">
				<span style="vertical-align:middle;font-size:18px;font-weight:700;color:#1e1f27;padding-left:6px;">2do AI</span>
				</td></tr>
				<tr><td style="background:#ffffff;border-radius:16px;padding:32px 28px;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;color:#1e1f27;">
				<h1 style="margin:0 0 12px;font-size:22px;line-height:1.3;font-weight:700;">%3$s</h1>
				<p style="margin:0 0 24px;font-size:15px;line-height:1.5;color:#5b5d6b;">%4$s</p>
				<table role="presentation" cellpadding="0" cellspacing="0" border="0"><tr>
				<td style="border-radius:10px;background:%11$s;">
				<a href="%9$s" style="display:inline-block;padding:14px 28px;font-size:16px;font-weight:600;color:#ffffff;text-decoration:none;border-radius:10px;">%5$s</a>
				</td></tr></table>
				<p style="margin:28px 0 8px;font-size:14px;color:#5b5d6b;">%6$s</p>
				<p style="margin:0 0 24px;padding:14px 0;background:#f3edff;border-radius:10px;text-align:center;font-family:'SF Mono',Menlo,Consolas,monospace;font-size:30px;font-weight:700;letter-spacing:6px;color:#6a1fe0;">%12$s</p>
				<p style="margin:0 0 8px;font-size:13px;line-height:1.5;color:#5b5d6b;">%7$s</p>
				<p style="margin:0;font-size:13px;line-height:1.5;color:#5b5d6b;">%8$s</p>
				</td></tr>
				<tr><td style="padding:20px 8px 0;font-family:-apple-system,BlinkMacSystemFont,'Segoe UI',Roboto,Helvetica,Arial,sans-serif;font-size:12px;line-height:1.5;color:#8a8c99;text-align:center;">%13$s</td></tr>
				</table>
				</td></tr>
				</table>
				</body>
				</html>
				""".formatted(
				escape(copy.lang()), escape(copy.preheader()), escape(copy.heading()), escape(copy.intro()),
				escape(copy.button()), escape(copy.codeLabel()), escape(copy.validity()), escape(copy.ignore()),
				escape(link), escape(logo), ACCENT, escape(code), escape(copy.tagline()));
	}

	/** Only what HTML requires; Polish letters stay as they are in a UTF-8 document. */
	private static String escape(String value) {
		return HtmlUtils.htmlEscape(value, "UTF-8");
	}
}
