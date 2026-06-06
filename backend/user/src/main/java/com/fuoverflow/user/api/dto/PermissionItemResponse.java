package com.fuoverflow.user.api.dto;

public record PermissionItemResponse(
        String slug,
        String module,
        String resource,
        String action,
        String description
) {
}
