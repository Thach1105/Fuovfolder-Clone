package com.fuoverflow.common.web;

import com.fuoverflow.common.config.SupportProperties;
import com.fuoverflow.common.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;

@RestControllerAdvice
public class GlobalExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

    private final SupportProperties support;

    public GlobalExceptionHandler(SupportProperties support) {
        this.support = support;
    }

    @ExceptionHandler(ApiException.class)
    public ResponseEntity<ApiResponse<Void>> handleApiException(ApiException exception, HttpServletRequest request) {
        return ResponseEntity.status(exception.status())
                .body(error(exception.code(), exception.getMessage(), request, null));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException exception, HttpServletRequest request) {
        List<ApiResponse.ErrorDetail.FieldError> fields = exception.getBindingResult().getFieldErrors().stream()
                .map(this::toFieldError)
                .toList();
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed", request, fields));
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ApiResponse<Void>> handleConstraintViolation(ConstraintViolationException exception, HttpServletRequest request) {
        return ResponseEntity.badRequest()
                .body(error("VALIDATION_ERROR", "Request validation failed", request, null));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleUnexpected(Exception exception, HttpServletRequest request) {
        log.error("Unhandled exception: method={}, path={}, requestId={}",
                request.getMethod(), request.getRequestURI(), request.getHeader("X-Request-Id"), exception);
        String message = buildSupportMessage(support);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(error("INTERNAL_ERROR", message, request, null));
    }

    private String buildSupportMessage(SupportProperties support) {
        boolean hasEmail = support.email() != null && !support.email().isBlank();
        boolean hasPhone = support.phone() != null && !support.phone().isBlank();
        if (hasEmail && hasPhone) {
            return String.format(
                "Đã xảy ra lỗi hệ thống. Vui lòng liên hệ hỗ trợ qua email %s hoặc số điện thoại %s.",
                support.email(), support.phone());
        } else if (hasEmail) {
            return String.format(
                "Đã xảy ra lỗi hệ thống. Vui lòng liên hệ hỗ trợ qua email %s.", support.email());
        } else if (hasPhone) {
            return String.format(
                "Đã xảy ra lỗi hệ thống. Vui lòng liên hệ hỗ trợ qua số điện thoại %s.", support.phone());
        } else {
            return "Đã xảy ra lỗi hệ thống. Vui lòng thử lại sau hoặc liên hệ quản trị viên.";
        }
    }

    private ApiResponse.ErrorDetail.FieldError toFieldError(FieldError error) {
        return new ApiResponse.ErrorDetail.FieldError(error.getField(), error.getDefaultMessage());
    }

    private ApiResponse<Void> error(String code, String message, HttpServletRequest request,
                                    List<ApiResponse.ErrorDetail.FieldError> fields) {
        ApiResponse.ErrorDetail detail = new ApiResponse.ErrorDetail(request.getHeader("X-Request-Id"), fields);
        return ApiResponse.failure(code, message, detail);
    }
}
