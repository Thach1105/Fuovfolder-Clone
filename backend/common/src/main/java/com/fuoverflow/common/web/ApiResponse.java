package com.fuoverflow.common.web;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.Instant;
import java.util.List;

/**
 * Standard response envelope wrapping every API payload.
 *
 * <p>Success responses carry the payload in {@code data}; error responses (built by
 * {@link GlobalExceptionHandler}) reuse the same shape with {@code success=false},
 * {@code data=null} and a populated {@code error}. {@code NON_NULL} keeps the common
 * success case lean (omits {@code message}/{@code error}); the primitive {@code success}
 * boolean is always serialized.
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record ApiResponse<T>(
        boolean success,
        String code,
        String message,
        T data,
        ErrorDetail error,
        Instant timestamp
) {
    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record ErrorDetail(
            String traceId,
            List<FieldError> fields
    ) {
        public record FieldError(String field, String message) {
        }
    }

    public static <T> ApiResponse<T> ok(T data) {
        return new ApiResponse<>(true, "OK", null, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> ok(T data, String message) {
        return new ApiResponse<>(true, "OK", message, data, null, Instant.now());
    }

    public static <T> ApiResponse<T> failure(String code, String message, ErrorDetail error) {
        return new ApiResponse<>(false, code, message, null, error, Instant.now());
    }
}
