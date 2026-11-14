package com.thedariusz.todoai.security;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Date;
import java.util.UUID;

import com.thedariusz.todoai.account.PerUserDataDeleter;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.UserRepository;
import org.jspecify.annotations.Nullable;
import org.springframework.security.web.authentication.rememberme.PersistentRememberMeToken;
import org.springframework.security.web.authentication.rememberme.PersistentTokenRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Spring Security's persistent remember-me store, over the project's own {@code remember_me_token}
 * table rather than the {@code persistent_logins} schema {@code JdbcTokenRepositoryImpl} assumes —
 * so the table follows the project's conventions (UUID v7 key, audit columns, a {@code user_id}
 * foreign key that makes FR-019 erasure checkable) instead of keying rows by a free-text username.
 *
 * <p>Spring's contract is keyed by username, which here is the account's e-mail address; this class
 * is where that is mapped to {@code user_id} and back.
 *
 * <p>Also the module's {@link PerUserDataDeleter}: a token is a way back into the account from any
 * browser that holds it, so it is erased with the account.
 */
@Component
class JpaPersistentTokenRepository implements PersistentTokenRepository, PerUserDataDeleter {

	private final RememberMeTokenRepository tokens;

	private final UserRepository users;

	JpaPersistentTokenRepository(RememberMeTokenRepository tokens, UserRepository users) {
		this.tokens = tokens;
		this.users = users;
	}

	/**
	 * Stores a freshly issued token and, in the same transaction, deletes the user's tokens that have
	 * expired — the only cleanup there is, run when the owner is signing in anyway.
	 */
	@Override
	@Transactional
	public void createNewToken(PersistentRememberMeToken token) {
		UUID userId = userIdOf(token.getUsername());
		OffsetDateTime issuedAt = toOffsetDateTime(token.getDate());
		tokens.deleteUnusedSince(userId, OffsetDateTime.now(ZoneOffset.UTC).minus(StableTokenRememberMeServices.VALIDITY));
		tokens.save(new RememberMeToken(userId, token.getSeries(), token.getTokenValue(), issuedAt));
	}

	@Override
	@Transactional
	public void updateToken(String series, String tokenValue, Date lastUsed) {
		tokens.recordUse(series, tokenValue, toOffsetDateTime(lastUsed));
	}

	@Override
	@Transactional(readOnly = true)
	public @Nullable PersistentRememberMeToken getTokenForSeries(String seriesId) {
		return tokens.findOwnedBySeries(seriesId)
				.map(owned -> new PersistentRememberMeToken(owned.email(), owned.series(), owned.tokenValue(),
						Date.from(owned.lastUsedAt().toInstant())))
				.orElse(null);
	}

	/**
	 * Logout. An address with no account is not an error: account deletion logs out after the row is
	 * gone, and by then {@link #deleteAllForUser} has already removed its tokens.
	 */
	@Override
	@Transactional
	public void removeUserTokens(String username) {
		users.findByEmail(Email.normalize(username)).ifPresent(user -> tokens.deleteByUserId(user.getId()));
	}

	@Override
	public void deleteAllForUser(UUID userId) {
		tokens.deleteByUserId(userId);
	}

	private UUID userIdOf(String username) {
		return users.findByEmail(Email.normalize(username))
				.orElseThrow(() -> new IllegalStateException("No account to remember for the signed-in user"))
				.getId();
	}

	private static OffsetDateTime toOffsetDateTime(Date date) {
		return date.toInstant().atOffset(ZoneOffset.UTC);
	}
}
