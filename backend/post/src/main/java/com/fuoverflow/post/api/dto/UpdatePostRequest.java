package com.fuoverflow.post.api.dto;

import jakarta.validation.constraints.NotBlank;

public record UpdatePostRequest(
        @NotBlank String body
) {
}
