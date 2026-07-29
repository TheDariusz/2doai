package com.thedariusz.todoai.security;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

import com.thedariusz.todoai.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adapts the domain {@link User} to Spring Security's authentication model. Carries the user's
 * <b>UUID</b> — the value the per-user isolation contract keys on ({@link CurrentUser#requireId()}
 * pulls it out of the security context) — alongside the email (Spring Security's {@code username})
 * and the stored password hash.
 *
 * <p>No authorities: the MVP is a flat multi-user model with no roles (see the plan's
 * <em>What We're NOT Doing</em>). All account-status flags are {@code true} — the account lifecycle
 * has no lock/expiry/disable states yet; FR-019 deletion removes the row outright.
 */
public record UserPrincipal(UUID userId, String email, String passwordHash) implements UserDetails {

	public static UserPrincipal from(User user) {
		return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash());
	}

	/**
	 * Masks the hash. The record's generated {@code toString()} would render it in full, and
	 * {@code AbstractAuthenticationToken.toString()} embeds the principal — so a single DEBUG log line
	 * or exception message would spill offline-crackable credential material into the logs.
	 */
	@Override
	public String toString() {
		return "UserPrincipal[userId=" + userId + ", email=" + email + "]";
	}

	@Override
	public Collection<? extends GrantedAuthority> getAuthorities() {
		return List.of();
	}

	@Override
	public String getPassword() {
		return passwordHash;
	}

	@Override
	public String getUsername() {
		return email;
	}

	@Override
	public boolean isAccountNonExpired() {
		return true;
	}

	@Override
	public boolean isAccountNonLocked() {
		return true;
	}

	@Override
	public boolean isCredentialsNonExpired() {
		return true;
	}

	@Override
	public boolean isEnabled() {
		return true;
	}
}
