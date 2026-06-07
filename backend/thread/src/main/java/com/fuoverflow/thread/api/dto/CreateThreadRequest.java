package com.fuoverflow.thread.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

public record CreateThreadRequest(
        @NotNull UUID categoryId,
        @NotBlank @Size(max = 300) String title,
        @NotBlank String body,
        @NotBlank String threadType,
        String campus,
        String semester,
        String materialType,
        @Size(max = 500) String tags,
        List<@NotBlank @Size(max = 255) String> pollOptions,
        Boolean watchThread,
        List<UUID> attachmentFileIds
) {
}
