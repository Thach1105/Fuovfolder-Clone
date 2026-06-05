package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record ReorderQuestionsRequest(
        @NotEmpty List<UUID> questionIds
) {
}
