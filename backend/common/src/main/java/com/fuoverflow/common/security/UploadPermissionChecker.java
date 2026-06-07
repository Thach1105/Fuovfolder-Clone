package com.fuoverflow.common.security;

import java.util.Set;
import java.util.UUID;

public interface UploadPermissionChecker {
    boolean hasAnyPermission(UUID userId, Set<String> permissionSlugs);
}
