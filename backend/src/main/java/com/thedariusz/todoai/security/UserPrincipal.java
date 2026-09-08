package com.thedariusz.todoai.security;

import java.util.Collection;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

import com.thedariusz.todoai.user.AppLanguage;
import com.thedariusz.todoai.user.User;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

/**
 * Adapts the domain {@link User} to Spring Security's authentication model. Carries the user's
 * <b>UUID</b> — the value the per-user isolation contract keys on ({@link CurrentUser#requireId()}
 * pulls it out of the security context) — alongside the email (Spring Security's {@code username}),
 * the stored password hash and the account's {@link AppLanguage}.
 *
 * <p><b>The language rides here so {@code GET /api/users/me} keeps costing zero queries.</b> The
 * login lookup already has the row in hand; carrying one more field off it is free, and every
 * avoided query afterwards is idle time the metered Neon compute can autosuspend through (see
 * {@code context/foundation/lessons.md}). It is also the one <em>mutable</em> component — FR-002
 * changes it mid-session, which is what {@link #withLanguage} and the equality override below are
 * for.
 *
 * <p>No authorities: the MVP is a flat multi-user model with no roles (see the plan's
 * <em>What We're NOT Doing</em>). Every account-status flag but one is {@code true} — there are no
 * lock or expiry states, and FR-019 deletion removes the row outright. The exception is
 * {@link #isEnabled()}, which since DEV-51 means <b>the address has been proved</b>: an account
 * nobody has confirmed cannot hold a session. When that check runs is as load-bearing as the flag —
 * see the {@code daoAuthenticationProvider} bean in {@code SecurityConfig}.
 */
public record UserPrincipal(UUID userId, String email, String passwordHash, AppLanguage language,
		boolean verified) implements UserDetails {

	/**
	 * Every component is required, and {@link #userId} is the one that would fail quietly without
	 * this: {@link #equals} dereferences it, so a principal built with a null id throws from inside a
	 * {@code SessionRegistry} lookup rather than at the point that built it. The others are stated for
	 * the same reason a NOT NULL column is — this is the record the whole request is authorized
	 * against, and "absent" is not one of the states it has.
	 */
	public UserPrincipal {
		Objects.requireNonNull(userId, "userId");
		Objects.requireNonNull(email, "email");
		Objects.requireNonNull(passwordHash, "passwordHash");
		Objects.requireNonNull(language, "language");
	}

	public static UserPrincipal from(User user) {
		return new UserPrincipal(user.getId(), user.getEmail(), user.getPasswordHash(), user.getLanguage(),
				user.isEmailVerified());
	}

	/** The principal the language switch (FR-002) puts back into the session's security context. */
	public UserPrincipal withLanguage(AppLanguage newLanguage) {
		return new UserPrincipal(userId, email, passwordHash, newLanguage, verified);
	}

	/**
	 * <b>Identity is the user id, not the tuple.</b> {@code SessionRegistry} is a map keyed on the
	 * principal, populated once at login by {@code RegisterSessionAuthenticationStrategy}, and FR-019
	 * deletion sweeps a user's other devices by looking their sessions up in it. The generated
	 * component-wise {@code equals} was harmless only while every component came from the same row and
	 * never changed; now that {@link #language} moves mid-session, a post-switch principal would miss
	 * its own registry entry and a phone left logged in would keep authenticating as a deleted
	 * account — while deletion still answered 204. Pinned by
	 * {@code AuthApiTest.endsEverySessionAfterALanguageSwitchHasReplacedThePrincipal}.
	 */
	@Override
	public boolean equals(Object other) {
		return other instanceof UserPrincipal principal && userId.equals(principal.userId);
	}

	@Override
	public int hashCode() {
		return userId.hashCode();
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

	/**
	 * <b>"The owner of this address confirmed it"</b> (DEV-51), which is what Spring Security's
	 * enabled/disabled flag has always meant here in everything but name. A false makes login fail with
	 * {@code DisabledException} — but only after the password matched, because {@code SecurityConfig}
	 * moves the check that reads this to post-authentication.
	 */
	@Override
	public boolean isEnabled() {
		return verified;
	}
}
