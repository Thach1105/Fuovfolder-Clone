package com.fuoverflow.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PointBalanceResponse(BigDecimal balance, Instant updatedAt) {
}
