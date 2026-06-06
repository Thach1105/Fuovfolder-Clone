package com.fuoverflow.user.api.dto;

public record UpdateUserOverridesRequest(
        java.util.List<UserOverrideItemRequest> overrides
) {
}
