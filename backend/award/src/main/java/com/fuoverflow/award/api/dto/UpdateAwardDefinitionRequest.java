package com.fuoverflow.award.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateAwardDefinitionRequest(
        @Size(max = 255) String name,
        String description,
        @Size(max = 500) String iconUrl,
        Boolean active
) {
}
