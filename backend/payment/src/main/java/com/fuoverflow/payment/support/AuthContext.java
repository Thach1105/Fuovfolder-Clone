package com.fuoverflow.payment.support;

import com.fuoverflow.common.exception.UnauthorizedException;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.UUID;

public final class AuthContext {
    private AuthContext() {
    }

    public static UUID currentUserId() {
        Authentication auth = currentAuthentication();
        try {
            return UUID.fromString(auth.getName());
        } catch (IllegalArgumentException ex) {
            throw new UnauthorizedException("AUTH_INVALID", "Invalid authentication token");
        }
    }

    public static boolean isAdmin() {
        return currentAuthentication().getAuthorities().stream()
                .anyMatch(authority -> "ROLE_admin".equals(authority.getAuthority())
                        || "ROLE_ADMIN".equals(authority.getAuthority()));
    }

    private static Authentication currentAuthentication() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("AUTH_REQUIRED", "Authentication required");
        }
        return auth;
    }
}
