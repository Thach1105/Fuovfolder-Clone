package com.fuoverflow.post.api.dto;

import jakarta.validation.constraints.NotBlank;

public record CreatePostRequest(
        @NotBlank String body
) {
}
