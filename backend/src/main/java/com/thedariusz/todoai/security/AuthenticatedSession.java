package com.thedariusz.todoai.security;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.SecurityContextRepository;
import org.springframework.stereotype.Component;

/**
 * The calling request's own authenticated session, for the one thing a handler legitimately does to
 * it after login: swap in a principal that has changed underneath it.
 *
 * <p><b>Why this is not two lines in a controller.</b> {@code GET /api/users/me} is answered from
 * the {@link UserPrincipal} held in the session and never queries the database, so any endpoint that
 * changes an account field the principal carries must put the new principal back — or the user's
 * change is real in Postgres and invisible everywhere they can see it until they log out. Replacing
 * the {@code Authentication} on the holder is not enough either: Spring Security 6 moved to
 * {@code SecurityContextHolderFilter}, which only <em>reads</em>, so the context has to be written
 * back through the chain's own {@link SecurityContextRepository}. That is a mechanism worth stating
 * once rather than rediscovering per endpoint — {@code SessionController} learned the same lesson
 * for login.
 */
@Component
public class AuthenticatedSession {

    /** The chain's own repository (a {@code SecurityConfig} bean), never a private instance. */
    private final SecurityContextRepository securityContextRepository;

    AuthenticatedSession(SecurityContextRepository securityContextRepository) {
        this.securityContextRepository = securityContextRepository;
    }

    /**
     * Re-authenticates the current session as {@code principal}, effective immediately and for the
     * rest of the session.
     *
     * <p>Only the <em>calling</em> session. A second device holds its own principal in its own
     * session and keeps the old value until it re-authenticates — {@code SessionRegistryImpl} tracks
     * session ids, not {@code HttpSession} handles, so reaching them would need a shared session
     * store this deployment deliberately does not have.
     *
     * <p>Deliberately does <b>not</b> rotate the session id or the CSRF token, unlike login. Both
     * rotations exist for the anonymous → authenticated privilege transition; there is no transition
     * here, and minting a new CSRF token mid-session would 403 the SPA's next in-flight mutation.
     */
    public void replacePrincipal(UserPrincipal principal, HttpServletRequest request,
            HttpServletResponse response) {

        Authentication current = SecurityContextHolder.getContext().getAuthentication();
        UsernamePasswordAuthenticationToken refreshed = UsernamePasswordAuthenticationToken.authenticated(
                principal, current.getCredentials(), current.getAuthorities());
        // Carried over rather than left null: the factory copies credentials and authorities but not
        // details, so the day anything attaches them at login (remote address, session id) this would
        // erase them mid-session for exactly the users who changed a setting.
        refreshed.setDetails(current.getDetails());

        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(refreshed);
        SecurityContextHolder.setContext(context);
        securityContextRepository.saveContext(context, request, response);
    }
}
