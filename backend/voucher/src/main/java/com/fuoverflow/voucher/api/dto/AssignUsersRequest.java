package com.fuoverflow.voucher.api.dto;

import jakarta.validation.constraints.NotEmpty;

import java.util.List;
import java.util.UUID;

public record AssignUsersRequest(
        @NotEmpty List<UUID> userIds
) {
}
