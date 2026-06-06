package com.fuoverflow.user.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UserOverrideItemRequest(
        @NotBlank String permissionSlug,
        @NotBlank String effect,
        String reason
) {
}
