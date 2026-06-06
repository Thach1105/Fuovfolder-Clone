package com.fuoverflow.auth.config;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.user.application.PermissionResolverService;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Before;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Aspect
@Component
public class PermissionSecurityAspect {
    private final PermissionResolverService permissionResolver;

    public PermissionSecurityAspect(PermissionResolverService permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    @Before("@annotation(requirePermission)")
    public void checkPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        Optional<UUID> userId = resolveUserId();
        if (userId.isEmpty()) {
            if (requirePermission.allowAnonymous()) {
                return;
            }
            throw new ForbiddenException("PERMISSION_DENIED", "Authentication required");
        }
        if (!permissionResolver.hasPermission(userId.get(), requirePermission.value())) {
            throw new ForbiddenException("PERMISSION_DENIED", "You do not have permission to perform this action");
        }
    }

    @Before("@within(requirePermission) && !@annotation(com.fuoverflow.common.security.RequirePermission)")
    public void checkClassPermission(JoinPoint joinPoint, RequirePermission requirePermission) {
        checkPermission(joinPoint, requirePermission);
    }

    private Optional<UUID> resolveUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return Optional.empty();
        }
        try {
            return Optional.of(UUID.fromString(authentication.getName()));
        } catch (IllegalArgumentException ex) {
            throw new ForbiddenException("PERMISSION_DENIED", "Authentication required");
        }
    }
}
