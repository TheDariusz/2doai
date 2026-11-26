package com.thedariusz.todoai.reminder;

/**
 * What one reminder's fire transaction decided (DEV-63), for the scheduler to act on once it has
 * committed. A failure is not an outcome: it propagates, and the scheduler retries the transaction.
 */
sealed interface FireOutcome {

	/** Nothing to send: the task is gone, done, withdrawn, already reminded, or past its expiry. */
	FireOutcome DROP = new Drop();

	/** The row is stamped; send this. */
	record Send(ReminderMail mail) implements FireOutcome {
	}

	/**
	 * The entry was stale — the row's term no longer yields it — and this is the window the row
	 * actually has. Arm it instead of sending early or dropping a reminder still owed.
	 */
	record Rearm(ReminderMoment.Window window) implements FireOutcome {
	}

	record Drop() implements FireOutcome {
	}
}
