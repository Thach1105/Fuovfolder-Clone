package com.fuoverflow.broadcast.api.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

public record AnnouncementRequest(
        @NotBlank String title,
        @NotBlank String contentHtml,
        @NotBlank String backgroundColor,
        String linkUrl,
        String linkLabel,
        int priority,
        @Min(10) int scrollSpeed,
        @Min(60) int stepSeconds,
        @NotNull Instant startAt,
        @NotNull Instant endAt
) {}
