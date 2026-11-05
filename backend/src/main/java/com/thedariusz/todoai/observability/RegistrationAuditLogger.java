package com.thedariusz.todoai.observability;

import com.thedariusz.todoai.user.UserVerified;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Logs {@code event=user_registered} once a human has proved an address: the line the "new
 * registration" alert in thedariusz-ops keys on. Registration alone is not the moment, for the same
 * reason {@link UserVerified} gives — an unproved row is anybody's typing.
 *
 * <p><b>After commit, on purpose.</b> {@code UserVerified} is published inside the verifying
 * transaction; a plain {@code @EventListener} would log even when something later in that
 * transaction rolls the verification back, and the owner would be paged about an account that does
 * not exist. This is the first {@code @TransactionalEventListener} in the codebase, and it differs
 * from {@code ProposalScheduler}'s plain listener deliberately: the rhythm writes inside the
 * transaction so its row rolls back with it, while a log line shipped to Loki cannot be rolled back
 * at all, so it may only be written once the outcome is final.
 *
 * <p>Only the account's UUID goes out; never the address.
 */
@Component
public class RegistrationAuditLogger {

	private static final Logger log = LoggerFactory.getLogger(RegistrationAuditLogger.class);

	@TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
	void onVerified(UserVerified event) {
		log.atInfo()
			.addKeyValue("event", "user_registered")
			.addKeyValue("user_id", event.userId())
			.log("user registered");
	}

}
