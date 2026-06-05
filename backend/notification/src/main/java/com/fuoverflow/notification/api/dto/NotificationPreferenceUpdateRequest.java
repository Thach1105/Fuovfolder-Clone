package com.fuoverflow.notification.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record NotificationPreferenceUpdateRequest(
        @NotNull @Valid List<Item> preferences
) {
    public record Item(
            @NotBlank String type,
            @NotBlank String channel,
            boolean enabled
    ) {
    }
}
