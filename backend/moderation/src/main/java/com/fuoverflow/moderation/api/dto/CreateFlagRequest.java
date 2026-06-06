package com.fuoverflow.moderation.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreateFlagRequest(
        @NotBlank String targetType,
        @NotNull UUID targetId,
        @NotBlank String reason,
        String note
) {
}
