package com.fuoverflow.coursera.api.dto;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.UUID;

public record CreateCourseraRequestBody(
        @NotNull UUID catalogItemId,
        @NotBlank @Email @Size(max = 255) String courseraEmail,
        @NotBlank @Size(min = 1, max = 255) String courseraPassword,
        @Size(max = 2000) String userNotes,
        String voucherCode
) {
}
