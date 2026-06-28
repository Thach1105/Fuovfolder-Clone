package com.fuoverflow.payment.api.dto;

import java.util.List;

public record AdminOrderPageResponse(
        List<AdminOrderListItemResponse> items,
        int page,
        int size,
        long totalElements,
        int totalPages
) {}
