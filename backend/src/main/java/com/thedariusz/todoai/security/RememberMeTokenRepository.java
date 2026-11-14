package com.thedariusz.todoai.security;

import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/**
 * The {@code remember_me_token} table. A {@link Repository}, not a {@code JpaRepository}, so it
 * publishes no unscoped finder by id (the {@code UserOwned} convention).
 *
 * <p>{@link #findBySeries} is the one read not scoped by user, and it cannot be: it runs on a request
 * that has no user yet, and the series is what says whose it is. That is the same position a session
 * id is in — a 128-bit random name, looked up to find the owner.
 */
interface RememberMeTokenRepository extends Repository<RememberMeToken, UUID> {

	RememberMeToken save(RememberMeToken token);

	Optional<RememberMeToken> findBySeries(String series);

	/**
	 * The series together with the address that owns it — the address is what Spring Security's
	 * contract keys a token by, and what the user is loaded again by.
	 */
	@Query("""
			select new com.thedariusz.todoai.security.RememberMeTokenRepository$Owned(
			    u.email, t.series, t.tokenValue, t.lastUsedAt)
			from RememberMeToken t, User u
			where u.id = t.userId and t.series = :series
			""")
	Optional<Owned> findOwnedBySeries(@Param("series") String series);

	/** Logout and FR-019 erasure: every remembered browser of the user goes at once. */
	@Modifying
	@Query("delete from RememberMeToken t where t.userId = :userId")
	int deleteByUserId(@Param("userId") UUID userId);

	/** The expiry sweep, run when the user signs in — there is no cleanup job (Neon idleness). */
	@Modifying
	@Query("delete from RememberMeToken t where t.userId = :userId and t.lastUsedAt < :cutoff")
	int deleteUnusedSince(@Param("userId") UUID userId, @Param("cutoff") OffsetDateTime cutoff);

	record Owned(String email, String series, String tokenValue, OffsetDateTime lastUsedAt) {
	}
}
