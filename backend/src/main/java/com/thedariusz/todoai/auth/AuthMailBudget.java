package com.thedariusz.todoai.auth;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import com.thedariusz.todoai.mail.MailboxProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

/**
 * A daily ceiling on auth mail (sign-in codes), so a burst of code requests cannot spend the mail
 * provider's daily allowance that proposal mail depends on. The Resend plan this app runs on allows
 * 100 messages a day for everything; {@code app.mail.auth-daily-budget} (60 in {@code application.properties}) is the share
 * auth mail may take.
 *
 * <p><b>Consulted after the address is looked up, and only for an address with an account</b>, on
 * the auth-mail executor, so a flood of made-up addresses spends nothing. An exhausted budget drops
 * the code and logs it ({@code event=auth_mail_code_dropped}) instead of refusing the request: the
 * answer stays the 202 every address gets, so it says nothing about which ones have accounts. Only a
 * send the provider would not take is {@linkplain #refund refunded}.
 *
 * <p>The day is UTC, the provider's quota day. In memory, like the throttle: there is one machine,
 * and a restart that resets the count early is a smaller harm than a database write per code request
 * on a database that bills for being awake.
 */
@Component
public class AuthMailBudget {

	private static final Logger log = LoggerFactory.getLogger(AuthMailBudget.class);

	private final int dailyBudget;

	private LocalDate day;

	private int spent;

	private boolean exhaustionLogged;

	@Autowired
	public AuthMailBudget(MailboxProperties mailbox) {
		this(mailbox.authDailyBudget());
	}

	/** The budget itself; bound and range-checked ({@code @Min(1)}) on {@link MailboxProperties}. */
	AuthMailBudget(int dailyBudget) {
		this.dailyBudget = dailyBudget;
	}

	/**
	 * Take one slot from today's budget.
	 *
	 * @return {@code true} when the code may be sent, {@code false} when today's budget is spent
	 */
	public synchronized boolean admit(Instant now) {
		rollOver(now);
		if (spent >= dailyBudget) {
			if (!exhaustionLogged) {
				// Once a day, not once per refusal: a flood would otherwise write a line per request.
				log.atWarn()
					.addKeyValue("event", "auth_mail_budget_exhausted")
					.addKeyValue("budget", dailyBudget)
					.log("The daily auth-mail budget is spent; codes for existing accounts are dropped until midnight UTC");
				exhaustionLogged = true;
			}
			return false;
		}
		spent++;
		return true;
	}

	/**
	 * Give back a slot whose message the provider would not take. A refund for a previous day is
	 * ignored: yesterday's failure is not today's credit.
	 */
	public synchronized void refund(Instant sentAt) {
		if (dayOf(sentAt).equals(day) && spent > 0) {
			spent--;
		}
	}

	private void rollOver(Instant now) {
		LocalDate today = dayOf(now);
		// Forward only: a request stamped before midnight can be admitted on the executor after one
		// stamped after it, and rolling back would hand it yesterday's count and reset today's.
		if (day == null || today.isAfter(day)) {
			day = today;
			spent = 0;
			exhaustionLogged = false;
		}
	}

	private static LocalDate dayOf(Instant moment) {
		return LocalDate.ofInstant(moment, ZoneOffset.UTC);
	}
}
