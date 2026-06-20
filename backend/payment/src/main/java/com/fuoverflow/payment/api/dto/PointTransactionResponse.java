package com.fuoverflow.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record PointTransactionResponse(String id, BigDecimal amount, String direction, String type, String description, Instant createdAt) {
}
