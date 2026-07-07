package com.fuoverflow.exam.api.dto;

import java.util.List;

public record AdminCommentPageResponse(
        List<AdminCommentResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {
}
