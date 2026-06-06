package com.fuoverflow.user.api.dto;

public record UserPermissionOverrideResponse(
        String permissionSlug,
        String effect,
        String reason
) {
}
