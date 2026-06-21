package com.fuoverflow.payment.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.payment.application.PaymentService;
import com.fuoverflow.payment.api.dto.CreatePaymentLinkRequest;
import com.fuoverflow.payment.api.dto.PayOSPaymentLinkResponse;
import com.fuoverflow.payment.api.dto.PaymentStatusResponse;
import com.fuoverflow.payment.support.AuthContext;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

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

    @GetMapping("/status")
    public ApiResponse<PaymentStatusResponse> getPaymentStatus(@RequestParam String orderCode) {
        UUID userId = AuthContext.currentUserId();
        return ApiResponse.ok(paymentService.getPaymentStatus(orderCode, userId, AuthContext.isAdmin()));
    }
}
