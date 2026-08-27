package com.thedariusz.todoai.user;

import java.util.UUID;

/**
 * An account has come into being. Published by registration and consumed by whatever has to start
 * happening for a user who did not exist a moment ago — today only the natural rhythm (S-05), which
 * holds its schedule in memory and would otherwise not see a new account until the next restart.
 *
 * <p>It lives in {@code user} rather than beside the service that publishes it ({@code auth}) so that
 * neither side has to depend on the other: both already depend on the aggregate the event is about.
 * It carries the id alone, not the {@link User} — an event that hands out a JPA entity hands out a
 * detached one, and its listeners start reasoning about the publisher's persistence context.
 */
public record UserRegistered(UUID userId) {
}
