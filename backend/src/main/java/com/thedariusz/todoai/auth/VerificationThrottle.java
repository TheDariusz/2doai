package com.thedariusz.todoai.auth;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.LinkedHashMap;
import java.util.Map;

import com.thedariusz.todoai.user.Email;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * How often one address may be sent a verification code (DEV-51). Two rules, both per address: a
 * cooldown between consecutive codes, and a ceiling per hour.
 *
 * <p><b>It is consulted before the address is even looked up — whether or not that address has an
 * account.</b> That is what keeps {@code POST /api/verification-codes} from becoming an enumeration
 * oracle: if only real accounts were counted, an unknown address would answer 202 forever while a
 * registered one started answering 429, and the difference is the answer to "does this address have
 * an account here". Only sends that were allowed are recorded — a refusal costs nothing, and a send
 * the provider would not take is {@linkplain #refund refunded}.
 *
 * <p>What it protects is the mailbox and the provider quota — a stranger's inbox must not be
 * floodable by re-posting the sign-up form, and the Resend daily allowance the natural rhythm
 * depends on must not be spendable by a bot.
 *
 * <p>ponytail: in memory and per address, like the scheduler's map — there is one machine, so there
 * is nothing to share it with. It buys nothing against an attacker willing to use a fresh address
 * each time; a per-IP limit at the Cloudflare edge is the next issue, and the real answer.
 *
 * <p><b>The map is capped, not merely expired.</b> Its keys are attacker-chosen strings arriving on a
 * public endpoint, so "entries leave an hour after their last send" is not a bound — the bound is
 * however many distinct addresses a flood can name in an hour, which on a 512 MB machine is the
 * memory-exhaustion lever. An access-ordered {@link LinkedHashMap} evicting past {@link #MAX_TRACKED}
 * gives a real ceiling. <b>{@code MAX_TRACKED} is a memory bound, not a throttle guarantee</b>:
 * under organic load the address evicted has been quiet far longer than {@link #WINDOW}, but a flood
 * of distinct addresses fills the map in minutes and can evict a real one mid-cooldown. The per-IP
 * limit at the edge is the real answer to that, and this is the ceiling that keeps the flood from
 * being a memory-exhaustion lever meanwhile. Expiry is per-key, on the deque the call already has in
 * hand, so {@link #admit} stays O(1) — a scan of every deque per call is what turns a flood into an
 * O(n²) one holding this class's monitor.
 */
@Component
public class VerificationThrottle {

	/** Long enough that a second press is a second press, short enough to read as "in a minute". */
	static final Duration COOLDOWN = Duration.ofSeconds(60);

	static final Duration WINDOW = Duration.ofHours(1);

	static final int MAX_PER_WINDOW = 5;

	/** The ceiling on addresses remembered at once — see the class javadoc on why there has to be one. */
	static final int MAX_TRACKED = 10_000;

	private static final Logger log = LoggerFactory.getLogger(VerificationThrottle.class);

	/** Address → the moments a code was sent to it inside the window, oldest first. */
	private final Map<String, Deque<Instant>> sends = new LinkedHashMap<>(64, 0.75f, true) {
		@Override
		protected boolean removeEldestEntry(Map.Entry<String, Deque<Instant>> eldest) {
			if (size() <= MAX_TRACKED) {
				return false;
			}
			// The moment the flood protection stops holding for somebody: the evicted address loses its
			// cooldown. Unobservable otherwise, and it is the signal that the edge limit is now the only
			// thing doing the work.
			log.warn("Verification throttle is full at {} addresses; evicting the one quiet longest",
					MAX_TRACKED);
			return true;
		}
	};

	/**
	 * Ask to send a code to this address, and record it when the answer is yes.
	 *
	 * @param email the recipient address, in any case — normalized here
	 * @param now the moment of the request
	 * @return {@code 0} when the code may be sent, otherwise the seconds the caller must wait
	 */
	synchronized long admit(String email, Instant now) {
		Deque<Instant> recent = bucketFor(email);
		expire(recent, now);
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
	 * Record a send that was never in question. The registration path cannot be refused — one row per
	 * address means a sign-up can no longer flood a mailbox — but the code it sends still starts the
	 * cooldown the "send again" button is measured against.
	 */
	synchronized void record(String email, Instant now) {
		Deque<Instant> recent = bucketFor(email);
		expire(recent, now);
		recent.addLast(now);
	}

	/**
	 * Un-record the most recent send for an address, because the provider would not take it. Without
	 * this an SMTP outage burns the hourly budget with nothing delivered, and the 503's advice that
	 * retrying is exactly the right thing to do becomes false — the retry would answer 429.
	 *
	 * <p>A targeted refund rather than recording on success only: an address is recorded before the row
	 * behind it is even looked up, and moving that after the send would make the endpoint the
	 * enumeration oracle this whole class exists to prevent.
	 *
	 * <p><b>It removes the caller's own send, not the newest one.</b> Two sends for one address can be
	 * in flight at once — a sign-up and a "send again" from a second tab — and popping the tail would
	 * have the failing one refund the send that actually arrived, leaving the address owed a cooldown
	 * nobody is serving.
	 *
	 * @param sentAt the moment this caller was recorded or admitted with; a send that is no longer in
	 *         the deque, having aged out of the window, is simply nothing to give back
	 */
	synchronized void refund(String email, Instant sentAt) {
		Deque<Instant> recent = sends.get(Email.normalize(email));
		if (recent != null) {
			recent.removeLastOccurrence(sentAt);
		}
	}

	/**
	 * Drop everything remembered about an address. Called when the account behind it is erased
	 * (FR-019): the address belongs to nobody again, and a cooldown outliving its owner would make
	 * the very next sign-up — by anyone — wait for a message that was sent to somebody else.
	 */
	public synchronized void forget(String email) {
		sends.remove(Email.normalize(email));
	}

	/**
	 * How many addresses are still remembered — expiry and eviction are only observable through it.
	 * An address whose sends have all aged out keeps an empty entry until it is evicted, so this is
	 * an upper bound on the addresses actually under a limit, never a lower one.
	 */
	synchronized int tracked() {
		return sends.size();
	}

	/**
	 * The deque for an address, keyed on exactly what registration stored. Normalizing here rather than
	 * trusting every caller is what keeps the limit from being bypassable by capitalization — a key
	 * contract stated only in a javadoc comment is one call site away from not holding.
	 */
	private Deque<Instant> bucketFor(String email) {
		return sends.computeIfAbsent(Email.normalize(email), address -> new ArrayDeque<>());
	}

	/** Drop the sends that have aged out of the window. Inclusive: one exactly a window old is done. */
	private static void expire(Deque<Instant> recent, Instant now) {
		Instant cutoff = now.minus(WINDOW);
		while (!recent.isEmpty() && !recent.peekFirst().isAfter(cutoff)) {
			recent.removeFirst();
		}
	}

	/** Never fractional-rounds down to "allowed": a moment still ahead is always at least one second. */
	private static long secondsUntil(Instant moment, Instant now) {
		return moment.isAfter(now) ? Math.max(1, Duration.between(now, moment).toSeconds()) : 0;
	}
}
