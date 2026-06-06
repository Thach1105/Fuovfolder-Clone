package com.fuoverflow.user.api.dto;

import java.util.List;
import java.util.UUID;

public record RoleDetailResponse(
        UUID id,
        String slug,
        String name,
        String roleType,
        UUID parentRoleId,
        boolean system,
        boolean editable,
        List<String> permissions
) {
}
