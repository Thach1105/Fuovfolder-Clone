package com.fuoverflow.user.api.dto;

import jakarta.validation.constraints.Size;

public record UpdateUserProfileRequest(
        @Size(max = 120) String displayName,
        @Size(max = 80) String firstName,
        @Size(max = 80) String lastName,
        @Size(max = 500) String avatarUrl
) {
}
