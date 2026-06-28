package com.fuoverflow.auth.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CompletePendingProfileRequest(
        @NotBlank @Size(min = 3, max = 64) String username,
        @NotBlank @Size(max = 120) String campus,
        @NotBlank @Size(max = 120) String displayName) {
}
