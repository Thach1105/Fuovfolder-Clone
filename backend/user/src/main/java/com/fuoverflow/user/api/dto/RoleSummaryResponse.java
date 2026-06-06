package com.fuoverflow.user.api.dto;

import java.util.UUID;

public record RoleSummaryResponse(
        UUID id,
        String slug,
        String name,
        String roleType,
        UUID parentRoleId,
        boolean system,
        boolean editable,
        int permissionCount
) {
}
