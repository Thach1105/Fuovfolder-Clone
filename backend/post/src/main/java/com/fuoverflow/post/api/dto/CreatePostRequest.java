package com.fuoverflow.post.api.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.UUID;

public record CreatePostRequest(
        @NotBlank String body,
        UUID parentPostId
) {
}
