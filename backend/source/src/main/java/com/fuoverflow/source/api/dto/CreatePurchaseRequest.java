package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.UUID;

public record CreatePurchaseRequest(
        @NotNull UUID catalogItemId
) {
}
