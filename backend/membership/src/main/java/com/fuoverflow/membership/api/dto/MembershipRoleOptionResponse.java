package com.fuoverflow.membership.api.dto;

import java.util.UUID;

public record MembershipRoleOptionResponse(
        UUID id,
        String slug,
        String name
) {
}
