package com.fuoverflow.forum.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateForumRequest(
        @NotBlank @Size(max = 120)
        String slug,

        @NotBlank @Size(max = 255)
        String title,

        @Size(max = 5000)
        String description,

        String visibility, // PUBLIC, MEMBERS, PRIVATE — defaults to PUBLIC

        Integer sortOrder  // defaults to 0
) {}
