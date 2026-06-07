package com.fuoverflow.award.api.dto;

import java.util.UUID;

public record AwardDefinitionResponse(
        UUID id,
        String slug,
        String name,
        String description,
        String iconUrl,
        String awardType,
        boolean active
) {
}
