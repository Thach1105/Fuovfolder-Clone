package com.fuoverflow.payment.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.payment.api.dto.AdminOrderDetailResponse;
import com.fuoverflow.payment.api.dto.AdminOrderPageResponse;
import com.fuoverflow.payment.api.dto.PaymentAnalyticsResponse;
import com.fuoverflow.payment.application.PaymentAdminService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/admin/payments")
@RequirePermission("admin.panel:access")
public class PaymentAdminController {

    private final PaymentAdminService service;

    public PaymentAdminController(PaymentAdminService service) {
        this.service = service;
    }

    @GetMapping("/orders")
    @RequirePermission("payment.admin:read")
    public ApiResponse<AdminOrderPageResponse> listOrders(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) UUID userId,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate) {
        return ApiResponse.ok(service.listOrders(status, userId, fromDate, toDate, page, Math.min(size, 100)));
    }

    @GetMapping("/orders/{orderId}")
    @RequirePermission("payment.admin:read")
    public ApiResponse<AdminOrderDetailResponse> getOrder(@PathVariable UUID orderId) {
        return ApiResponse.ok(service.getOrder(orderId));
    }

    @GetMapping("/analytics")
    @RequirePermission("payment.admin:analytics")
    public ApiResponse<PaymentAnalyticsResponse> getAnalytics(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate) {
        return ApiResponse.ok(service.getAnalytics(fromDate, toDate));
    }
}
