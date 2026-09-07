package com.thedariusz.todoai.security;

import java.util.Optional;
import java.util.UUID;

import com.thedariusz.todoai.user.AppLanguage;
import com.thedariusz.todoai.user.Email;
import com.thedariusz.todoai.user.User;
import com.thedariusz.todoai.user.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Unit tests for the login lookup: the submitted username must go through the same normalization
 * the {@link Email} VO applied at registration (strip + lowercase), otherwise a login that differs
 * only in case or surrounding whitespace fails to find its own row.
 */
class AppUserDetailsServiceTest {

	private static final String STORED_EMAIL = "alice@example.com";

	private final UserRepository users = mock(UserRepository.class);

	private final AppUserDetailsService service = new AppUserDetailsService(users);

	/**
	 * A row as the lookup actually receives one — Hibernate-loaded, and therefore with its id set.
	 * The entity constructor cannot assign one ({@code @UuidGenerator} does that on persist), so the
	 * fixture does what persisting would have. Without it the lookup is handed a user that could not
	 * have come out of the database, and {@link UserPrincipal}'s own invariant rejects it.
	 */
	private static User stored() {
		User user = new User(Email.of(STORED_EMAIL), "{bcrypt}$2a$10$hash", AppLanguage.PL);
		ReflectionTestUtils.setField(user, "id", UUID.randomUUID());
		return user;
	}

	@Test
	void normalizesCaseAndWhitespaceBeforeLookup() {
		when(users.findByEmail(STORED_EMAIL))
				.thenReturn(Optional.of(stored()));

		UserDetails details = service.loadUserByUsername("  Alice@Example.COM  ");

		assertThat(details.getUsername()).isEqualTo(STORED_EMAIL);
	}

	/**
	 * The account's language rides on the principal so {@code GET /api/users/me} can report it
	 * without a query — the login lookup is the one round-trip that already has the row in hand, and
	 * every avoided query afterwards is idle time Neon can autosuspend through (lessons.md).
	 */
	@Test
	void carriesTheAccountsLanguageOntoThePrincipal() {
		when(users.findByEmail(STORED_EMAIL))
				.thenReturn(Optional.of(stored()));

		UserDetails details = service.loadUserByUsername(STORED_EMAIL);

		assertThat(((UserPrincipal) details).language()).isEqualTo(AppLanguage.PL);
	}

	@Test
	void throwsWhenNoUserMatches() {
		when(users.findByEmail(anyString())).thenReturn(Optional.empty());

		assertThatThrownBy(() -> service.loadUserByUsername("ghost@example.com"))
				.isInstanceOf(UsernameNotFoundException.class);
	}
}
