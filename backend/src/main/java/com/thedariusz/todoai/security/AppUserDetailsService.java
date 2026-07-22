package com.thedariusz.todoai.security;

import java.util.Locale;

import com.thedariusz.todoai.user.UserRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Bridges the domain {@link UserRepository} to Spring Security's authentication: loads a
 * {@link UserPrincipal} by email so the {@code DaoAuthenticationProvider} can compare the
 * submitted password against the stored hash.
 *
 * <p>The submitted username is lowercased ({@code Locale.ROOT}) before lookup so login is
 * case-insensitive and matches the normalized value the {@code Email} VO stored. A missing user
 * raises {@link UsernameNotFoundException}, which the provider turns into a generic
 * {@code BadCredentialsException} (no user-enumeration on login).
 */
@Service
public class AppUserDetailsService implements UserDetailsService {

	private final UserRepository users;

	public AppUserDetailsService(UserRepository users) {
		this.users = users;
	}

	@Override
	@Transactional(readOnly = true)
	public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
		return users.findByEmail(username.toLowerCase(Locale.ROOT))
				.map(UserPrincipal::from)
				.orElseThrow(() -> new UsernameNotFoundException("No user for the supplied credentials"));
	}
}
