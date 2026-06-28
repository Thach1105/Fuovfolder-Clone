package com.fuoverflow.payment.api.dto;

import java.util.List;

public record DepositHistoryPageResponse(
        List<DepositHistoryItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
