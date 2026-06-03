package com.fuoverflow.forum.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateCategoryRequest(
        @Size(max = 255)
        String title,

        @Size(max = 5000)
        String description,

        Integer sortOrder,

        String visibility // PUBLIC, MEMBERS, PRIVATE
) {}
