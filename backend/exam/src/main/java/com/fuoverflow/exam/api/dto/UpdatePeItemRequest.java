package com.fuoverflow.exam.api.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

public record UpdatePeItemRequest(
        @Size(max = 500) String title,
        String description,
        List<String> examImageUrls,
        Integer sortOrder
) {
}
