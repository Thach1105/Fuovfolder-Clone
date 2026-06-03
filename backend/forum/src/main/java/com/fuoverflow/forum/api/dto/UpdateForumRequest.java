package com.fuoverflow.forum.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateForumRequest(
        @Size(max = 255)
        String title,

        @Size(max = 5000)
        String description,

        String visibility, // PUBLIC, MEMBERS, PRIVATE

        Integer sortOrder
) {}
