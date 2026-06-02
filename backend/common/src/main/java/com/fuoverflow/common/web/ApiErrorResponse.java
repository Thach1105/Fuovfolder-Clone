package com.fuoverflow.common.web;

import java.time.Instant;
import java.util.List;

public record ApiErrorResponse(
        String code,
        String message,
        String traceId,
        Instant timestamp,
        List<FieldErrorItem> fields
) {
    public record FieldErrorItem(String field, String message) {
    }
}
