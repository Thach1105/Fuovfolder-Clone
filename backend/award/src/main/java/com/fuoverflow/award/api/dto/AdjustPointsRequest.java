package com.fuoverflow.award.api.dto;

import jakarta.validation.constraints.NotNull;

public record AdjustPointsRequest(
        @NotNull Integer delta,
        String reason
) {
}
