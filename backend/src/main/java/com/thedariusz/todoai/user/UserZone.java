package com.thedariusz.todoai.user;

import java.time.ZoneId;

/**
 * Whose day the app means when it says "morning" or "today" — the one place that is decided. Lifted
 * out of {@code ProposalRhythm} when the due-date reminder (DEV-63) became the second thing that
 * needs it: the zone answers a question about the user, not about proposals.
 *
 * <p>ponytail: one hardcoded zone while every account is Polish; a {@code user.timezone} column is
 * the upgrade, and this constant is what it replaces.
 */
public final class UserZone {

	public static final ZoneId ZONE = ZoneId.of("Europe/Warsaw");

	private UserZone() {
	}
}
