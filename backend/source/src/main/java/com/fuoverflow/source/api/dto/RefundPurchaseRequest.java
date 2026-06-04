package com.fuoverflow.source.api.dto;

import jakarta.validation.constraints.Size;

public record RefundPurchaseRequest(
        @Size(max = 500) String reason
) {
}
