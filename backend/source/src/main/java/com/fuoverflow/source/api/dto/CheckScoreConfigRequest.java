package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckScoreConfigRequest(
        String authorizeKey,
        @NotBlank String checkScoreUrl
) {
}
