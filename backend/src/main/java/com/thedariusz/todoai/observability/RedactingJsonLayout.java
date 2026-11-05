package com.thedariusz.todoai.observability;

import java.util.regex.Pattern;

import ch.qos.logback.classic.spi.ILoggingEvent;
import com.github.loki4j.logback.JsonLayout;

/**
 * loki4j's JSON line with the local part of every email address replaced, so that
 * {@code jan@example.com} leaves the machine as {@code <redacted>@example.com}.
 *
 * <p>The app's own log lines already name only a domain. What they cannot control is the text of a
 * third-party exception, in {@code err} and in the stack trace inside {@code msg}: Spring's {@code MailSendException} quotes the
 * SMTP server's reply, and a rejected recipient's reply quotes the address. Redacting the finished
 * line covers every field and every cause at once, which no per-call-site rule can. The domain stays,
 * as in the app's own lines — it is what tells one provider's outage from one mailbox's bounce.
 */
public class RedactingJsonLayout extends JsonLayout {

	private static final Pattern LOCAL_PART = Pattern.compile("[A-Za-z0-9._%+-]+(?=@[A-Za-z0-9-]+(\\.[A-Za-z0-9-]+)+)");

	@Override
	public String doLayout(ILoggingEvent event) {
		return LOCAL_PART.matcher(super.doLayout(event)).replaceAll("<redacted>");
	}

}
