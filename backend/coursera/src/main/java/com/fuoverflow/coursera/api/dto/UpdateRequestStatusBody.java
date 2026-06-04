package com.fuoverflow.coursera.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record UpdateRequestStatusBody(
        @NotBlank String status,
        @Size(max = 2000) String note,
        java.util.UUID assignedToUserId
) {
}
