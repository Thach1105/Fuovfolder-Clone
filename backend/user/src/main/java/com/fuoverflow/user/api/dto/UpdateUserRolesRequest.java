package com.fuoverflow.user.api.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateUserRolesRequest(@NotNull List<String> roleSlugs) {
}
