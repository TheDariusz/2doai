package com.thedariusz.todoai.auth;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * A daily ceiling on auth mail (sign-in codes), so a burst of code requests cannot spend the mail
 * provider's daily allowance that proposal mail depends on. The Resend plan this app runs on allows
 * 100 messages a day for everything; {@code app.mail.auth-daily-budget} (60 by default) is the share
 * auth mail may take.
 *
 * <p><b>Consulted before the address is looked up</b>, like {@link VerificationThrottle}, so an
 * exhausted budget answers the same 503 for every address and says nothing about which ones have
 * accounts. Only a send the provider would not take is {@linkplain #refund refunded}; a request for an
 * address with no account still spends its slot, exactly as it spends a throttle entry.
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

	public AuthMailBudget(@Value("${app.mail.auth-daily-budget:60}") int dailyBudget) {
		if (dailyBudget < 1) {
			throw new IllegalArgumentException("app.mail.auth-daily-budget must be at least 1, was " + dailyBudget);
		}
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
					.log("The daily auth-mail budget is spent; code requests answer 503 until midnight UTC");
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
		if (!today.equals(day)) {
			day = today;
			spent = 0;
			exhaustionLogged = false;
		}
	}

	private static LocalDate dayOf(Instant moment) {
		return LocalDate.ofInstant(moment, ZoneOffset.UTC);
	}
}
