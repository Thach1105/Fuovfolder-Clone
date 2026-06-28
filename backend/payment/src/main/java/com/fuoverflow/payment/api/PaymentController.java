package com.fuoverflow.payment.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.payment.application.PaymentService;
import com.fuoverflow.payment.api.dto.CreateCustomPaymentLinkRequest;
import com.fuoverflow.payment.api.dto.CreatePaymentLinkRequest;
import com.fuoverflow.payment.api.dto.DepositHistoryPageResponse;
import com.fuoverflow.payment.api.dto.DepositResumeResponse;
import com.fuoverflow.payment.api.dto.PayOSPaymentLinkResponse;
import com.fuoverflow.payment.api.dto.PaymentStatusResponse;
import com.fuoverflow.payment.support.AuthContext;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payment")
public class PaymentController {

    private final PaymentService paymentService;

    public PaymentController(PaymentService paymentService) {
        this.paymentService = paymentService;
    }

    @PostMapping("/create")
    public ApiResponse<PayOSPaymentLinkResponse> createPaymentLink(@RequestBody @Valid CreatePaymentLinkRequest request) {
        UUID userId = AuthContext.currentUserId();
        PayOSPaymentLinkResponse response = paymentService.createPaymentLink(
                request.tierId(),
                request.returnUrl(),
                request.cancelUrl(),
                userId
        );
        return ApiResponse.ok(response);
    }

    @PostMapping("/create-custom")
    public ApiResponse<PayOSPaymentLinkResponse> createCustomPaymentLink(@RequestBody @Valid CreateCustomPaymentLinkRequest request) {
        UUID userId = AuthContext.currentUserId();
        PayOSPaymentLinkResponse response = paymentService.createCustomPaymentLink(
                request.amountVnd(),
                request.returnUrl(),
                request.cancelUrl(),
                userId
        );
        return ApiResponse.ok(response);
    }

    @GetMapping("/status")
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(@RequestParam String orderCode) {
        UUID userId = AuthContext.currentUserId();
        return ApiResponse.ok(paymentService.getPaymentStatus(orderCode, userId, AuthContext.isAdmin()));
    }

    @GetMapping("/me/deposits")
    public ApiResponse<DepositHistoryPageResponse> listMyDeposits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant fromDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant toDate) {
        UUID userId = AuthContext.currentUserId();
        return ApiResponse.ok(paymentService.listUserDeposits(userId, status, fromDate, toDate, page, Math.min(size, 100)));
    }

    @GetMapping("/me/deposits/{orderId}/resume")
    public ApiResponse<DepositResumeResponse> resumeDeposit(@PathVariable UUID orderId) {
        UUID userId = AuthContext.currentUserId();
        return ApiResponse.ok(paymentService.getResumeInfo(orderId, userId));
    }
}
