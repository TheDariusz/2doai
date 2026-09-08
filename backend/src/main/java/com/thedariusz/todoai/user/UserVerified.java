package com.thedariusz.todoai.user;

import java.util.UUID;

/**
 * An account has proved its address, and is from this moment one the app may act on unprompted.
 * Consumed by whatever has to start happening for such an account — today only the natural rhythm
 * (S-05), which holds its schedule in memory and would otherwise not see it until the next restart.
 *
 * <p><b>Verification, not registration, is the moment.</b> Registration alone creates a row for an
 * address nobody has consented with, and the one thing the rhythm does is write to that address
 * without being asked — so an account adopted at sign-up would make the app a spam relay to any
 * mailbox a bot cares to type in (DEV-51). Nothing but the code itself ever reaches an unproved
 * address.
 *
 * <p>It lives in {@code user} rather than beside the service that publishes it ({@code auth}) so that
 * neither side has to depend on the other: both already depend on the aggregate the event is about.
 * It carries the id alone, not the {@link User} — an event that hands out a JPA entity hands out a
 * detached one, and its listeners start reasoning about the publisher's persistence context.
 */
public record UserVerified(UUID userId) {
}
