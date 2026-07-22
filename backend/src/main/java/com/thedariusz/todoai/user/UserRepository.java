package com.thedariusz.todoai.user;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

/**
 * Spring Data access to the {@link User} aggregate. Backs authentication (login lookup by email
 * via the {@code AppUserDetailsService}) and registration's duplicate-email guard.
 *
 * <p>Both finders key on the normalized (lowercased) email, so callers must pass an already-
 * normalized value (the {@code Email} VO or an {@code AppUserDetailsService} that lowercases the
 * supplied username). The {@code app_user.email} UNIQUE index serves both lookups — no extra index.
 */
public interface UserRepository extends JpaRepository<User, UUID> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);
}
