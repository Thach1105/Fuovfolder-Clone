package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;
import java.util.UUID;

public record SetRelatedItemsRequest(
        @NotNull List<UUID> relatedIds
) {
}
