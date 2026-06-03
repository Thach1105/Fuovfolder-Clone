package com.fuoverflow.forum.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCategoryRequest(
        @NotBlank @Size(max = 120)
        String slug,

        @NotBlank @Size(max = 255)
        String title,

        @Size(max = 5000)
        String description,

        Integer sortOrder,

        String visibility // PUBLIC, MEMBERS, PRIVATE — defaults to PUBLIC
) {}
