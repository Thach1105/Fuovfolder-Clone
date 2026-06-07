package com.fuoverflow.user.application;

import com.fuoverflow.common.security.UploadPermissionChecker;
import org.springframework.stereotype.Service;

import java.util.Set;
import java.util.UUID;

@Service
public class UserUploadPermissionChecker implements UploadPermissionChecker {
    private final PermissionResolverService permissionResolver;

    public UserUploadPermissionChecker(PermissionResolverService permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    @Override
    public boolean hasAnyPermission(UUID userId, Set<String> permissionSlugs) {
        for (String permission : permissionSlugs) {
            if (permissionResolver.hasPermission(userId, permission)) {
                return true;
            }
        }
        return false;
    }
}
