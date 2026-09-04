package com.thedariusz.todoai.user;

import java.time.OffsetDateTime;
import java.util.UUID;

import org.springframework.security.authentication.AuthenticationCredentialsNotFoundException;
import org.springframework.stereotype.Service;

/**
 * Changes to an account's own settings — the writes {@code PATCH /api/users/me} performs, with the
 * "what does no matching row mean" rule they share.
 *
 * <p>Its own class rather than a handful of lines in {@link UserController} because the controller
 * is the router: {@code GoalController} and {@code ProposalController} both delegate, and the moment
 * a second setting lands (display name, timezone) this is where its write and its 0-row rule go
 * without the handler growing a second copy of either.
 */
@Service
public class UserSettingsService {

    private final UserRepository users;

    UserSettingsService(UserRepository users) {
        this.users = users;
    }

    /**
     * FR-002 — moves the account's language. A targeted update rather than a {@code save} of a
     * loaded {@link User}, so {@code User} keeps having no setters at all and no call site is one
     * {@code save} away from re-inserting a deleted account.
     *
     * <p><b>The write is unconditional, and the obvious optimisation is a trap.</b> Adding
     * {@code and u.preferredLanguage <> :language} to the query would skip a redundant write — and
     * would also match 0 rows for it, which this method reads as "the account is gone": a user
     * re-selecting the language they already have would be logged out. Keeping a same-value switch
     * off the wire is therefore the <em>client's</em> job, and it matters because a query is what
     * wakes the metered Neon compute ({@code context/foundation/lessons.md}, "Let a scale-to-zero
     * database actually sleep"). A person pressing this by hand is rare; an SPA reconciling the
     * browser locale against the account on every boot would not be.
     *
     * @throws AuthenticationCredentialsNotFoundException when no row matched — the account was
     *         erased between this session being established and this request arriving. An
     *         {@code AuthenticationException} so the {@code ExceptionTranslationFilter} answers
     *         <b>401</b>, which is the truth: there is no longer an account to be authenticated as.
     *         The same exception {@code CurrentUser.requireId()} raises, for the same reason.
     */
    public void changeLanguage(UUID userId, AppLanguage language) {
        if (users.updateLanguage(userId, language, OffsetDateTime.now()) == 0) {
            throw new AuthenticationCredentialsNotFoundException("The account no longer exists");
        }
    }
}
