package com.thedariusz.todoai.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.Map;

import org.springframework.stereotype.Component;

/**
 * How often one address may be sent a verification code (DEV-51). Two rules, both per address: a
 * cooldown between consecutive codes, and a ceiling per hour.
 *
 * <p><b>It is consulted before the address is even looked up, and it records the attempt either
 * way.</b> That is what keeps {@code POST /api/verification-codes} from becoming an enumeration
 * oracle: if only real accounts were counted, an unknown address would answer 202 forever while a
 * registered one started answering 429, and the difference is the answer to "does this address have
 * an account here".
 *
 * <p>What it protects is the mailbox and the provider quota — a stranger's inbox must not be
 * floodable by re-posting the sign-up form, and the Resend daily allowance the natural rhythm
 * depends on must not be spendable by a bot.
 *
 * <p>ponytail: in memory and per address, like the scheduler's map — there is one machine, so there
 * is nothing to share it with. It buys nothing against an attacker willing to use a fresh address
 * each time; a per-IP limit at the Cloudflare edge is the next issue. The map is pruned on every
 * call rather than on a timer: unbounded and keyed on attacker-chosen strings, it is the
 * memory-exhaustion lever on a 512 MB machine.
 */
@Component
class VerificationThrottle {

	/** Long enough that a second press is a second press, short enough to read as "in a minute". */
	static final Duration COOLDOWN = Duration.ofSeconds(60);

	static final Duration WINDOW = Duration.ofHours(1);

	static final int MAX_PER_WINDOW = 5;

	/** Address → the moments a code was sent to it inside the window, oldest first. */
	private final Map<String, Deque<Instant>> sends = new HashMap<>();

	/**
	 * Ask to send a code to this address, and record it when the answer is yes.
	 *
	 * @param email the normalized recipient address
	 * @param now the moment of the request
	 * @return {@code 0} when the code may be sent, otherwise the seconds the caller must wait
	 */
	synchronized long admit(String email, Instant now) {
		prune(now);
		Deque<Instant> recent = sends.computeIfAbsent(email, address -> new ArrayDeque<>());
		if (!recent.isEmpty()) {
			long cooldown = secondsUntil(recent.peekLast().plus(COOLDOWN), now);
			if (cooldown > 0) {
				return cooldown;
			}
		}
		if (recent.size() >= MAX_PER_WINDOW) {
			return secondsUntil(recent.peekFirst().plus(WINDOW), now);
		}
		recent.addLast(now);
		return 0;
	}

	/**
	 * Drop everything remembered about an address. Called when the account behind it is erased
	 * (FR-019): the address belongs to nobody again, and a cooldown outliving its owner would make
	 * the very next sign-up — by anyone — wait for a message that was sent to somebody else.
	 */
	synchronized void forget(String email) {
		sends.remove(email);
	}

	/** How many addresses are still remembered — the pruning above is only observable through  */
	synchronized int tracked() {
		return sends.size();
	}

	private void prune(Instant now) {
		Instant cutoff = now.minus(WINDOW);
		// Inclusive: a send exactly one window old has served its whole sentence.
		sends.values().forEach(recent -> recent.removeIf(sent -> !sent.isAfter(cutoff)));
		sends.values().removeIf(Deque::isEmpty);
	}

	/** Never fractional-rounds down to "allowed": a moment still ahead is always at least one second. */
	private static long secondsUntil(Instant moment, Instant now) {
		return moment.isAfter(now) ? Math.max(1, Duration.between(now, moment).toSeconds()) : 0;
	}
}
