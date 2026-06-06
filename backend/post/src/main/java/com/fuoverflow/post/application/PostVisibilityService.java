package com.fuoverflow.post.application;

import com.fuoverflow.user.application.PermissionResolverService;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class PostVisibilityService {
    private static final List<String> PUBLIC_STATUSES = List.of("visible");

    private final PermissionResolverService permissionResolver;

    public PostVisibilityService(PermissionResolverService permissionResolver) {
        this.permissionResolver = permissionResolver;
    }

    public List<String> listStatusesForViewer(UUID viewerUserId) {
        if (viewerUserId == null) {
            return PUBLIC_STATUSES;
        }
        if (permissionResolver.hasPermission(viewerUserId, "forum.moderation:read")) {
            return List.of("visible", "pending", "hidden", "flagged");
        }
        return List.of("visible", "pending");
    }

    public boolean canViewPost(UUID viewerUserId, UUID authorUserId, String status) {
        if ("visible".equals(status)) {
            return true;
        }
        if (viewerUserId == null) {
            return false;
        }
        if (viewerUserId.equals(authorUserId) && "pending".equals(status)) {
            return true;
        }
        return permissionResolver.hasPermission(viewerUserId, "forum.moderation:read");
    }
}
