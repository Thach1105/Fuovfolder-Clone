package com.fuoverflow.user.api.dto;

import java.util.List;

public record AdminUserPageResponse(
        List<AdminUserSummaryResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
