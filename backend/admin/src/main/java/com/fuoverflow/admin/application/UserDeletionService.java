package com.fuoverflow.admin.application;

import com.fuoverflow.auth.persistence.UserSessionRepository;
import com.fuoverflow.user.application.PermissionResolverService;
import com.fuoverflow.user.application.UserAdminService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates an admin-initiated account removal: soft-delete the user, then revoke
 * every active session so existing access/refresh tokens die immediately, and drop the
 * cached permissions for that user. Lives in the admin module because it spans the
 * {@code user} and {@code auth} modules.
 */
@Service
public class UserDeletionService {
    private static final String REVOKE_REASON = "ADMIN_USER_DELETED";

    private final UserAdminService userAdminService;
    private final UserSessionRepository userSessionRepository;
    private final PermissionResolverService permissionResolver;

    public UserDeletionService(UserAdminService userAdminService,
                               UserSessionRepository userSessionRepository,
                               PermissionResolverService permissionResolver) {
        this.userAdminService = userAdminService;
        this.userSessionRepository = userSessionRepository;
        this.permissionResolver = permissionResolver;
    }

    @Transactional
    public void softDeleteUser(UUID targetId, UUID actingAdminId) {
        userAdminService.softDeleteUser(targetId, actingAdminId);
        userSessionRepository.revokeAllByUserId(targetId, REVOKE_REASON, Instant.now());
        permissionResolver.invalidate(targetId);
    }
}
