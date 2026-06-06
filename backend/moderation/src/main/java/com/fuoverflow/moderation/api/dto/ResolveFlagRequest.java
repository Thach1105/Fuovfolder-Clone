package com.fuoverflow.moderation.api.dto;

import jakarta.validation.constraints.NotBlank;

public record ResolveFlagRequest(
        @NotBlank String action,
        String reason
) {
}
