package com.fuoverflow.payment.api.dto;

import java.math.BigDecimal;
import java.time.Instant;

public record DepositPointResponse(String id, BigDecimal amount, String type, String description, Instant createdAt) {
}