package com.fuoverflow.user.api.dto;

import java.util.List;
import java.util.UUID;

public record EffectivePermissions(
        UUID userId,
        long permVersion,
        List<String> roles,
        List<String> permissions,
        boolean superAdmin
) {
}
