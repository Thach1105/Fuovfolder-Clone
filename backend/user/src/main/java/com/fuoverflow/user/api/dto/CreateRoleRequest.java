package com.fuoverflow.user.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.util.List;

public record CreateRoleRequest(
        @NotBlank String slug,
        @NotBlank String name,
        String parentRoleSlug,
        @NotNull List<String> permissions
) {
}
