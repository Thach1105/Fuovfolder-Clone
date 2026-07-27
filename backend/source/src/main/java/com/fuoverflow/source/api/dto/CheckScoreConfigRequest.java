package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CheckScoreConfigRequest(
        String authorizeKey,
        String xsrfCookie,
        @NotBlank String checkScoreUrl
) {
}
