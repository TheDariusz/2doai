package com.thedariusz.todoai.security;

import com.thedariusz.todoai.user.Email;
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
 * <p>The submitted username is normalized through {@link Email#normalize} before lookup — the very
 * method the {@code Email} VO normalizes with at registration, not a copy of it — so login is
 * case- and whitespace-insensitive and matches the stored value. A missing user
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
		return users.findByEmail(Email.normalize(username))
				.map(UserPrincipal::from)
				.orElseThrow(() -> new UsernameNotFoundException("No user for the supplied credentials"));
	}
}
