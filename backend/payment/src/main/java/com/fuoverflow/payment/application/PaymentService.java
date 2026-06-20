package com.fuoverflow.payment.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.payment.api.dto.PayOSPaymentLinkResponse;
import com.fuoverflow.payment.api.dto.PaymentStatusResponse;
import com.fuoverflow.payment.persistence.*;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

@Service
public class PaymentService {

    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final PayOS payOS;
    private final PointService pointService;

    public PaymentService(OrderRepository orderRepo, PaymentRepository paymentRepo,
                          PayOS payOS, PointService pointService) {
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.payOS = payOS;
        this.pointService = pointService;
    }

    @Transactional
    public PayOSPaymentLinkResponse createPaymentLink(BigDecimal amount, String description,
                                                        String returnUrl, String cancelUrl, UUID userId) {
        try {
            long orderCode = System.currentTimeMillis();
            String idempotencyKey = "idempotent-" + orderCode;

            OrderEntity order = OrderEntity.create(userId,
                    amount.multiply(new BigDecimal(100)).intValue(),
                    "VND", "payos", String.valueOf(orderCode));
            order.setIdempotencyKey(idempotencyKey);
            orderRepo.save(order);

            CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amount.longValue())
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);

            return new PayOSPaymentLinkResponse(
                    response.getCheckoutUrl(),
                    response.getQrCode(),
                    String.valueOf(orderCode)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create payment link: " + e.getMessage(), e);
        }
    }

    @Transactional
    public void confirmPayment(String orderCode, UUID userId) {
        OrderEntity order = orderRepo.findByProviderOrderId(orderCode)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found for code: " + orderCode));

        if (!"pending".equals(order.getStatus())) {
            return;
        }

        PaymentEntity payment = PaymentEntity.create(
                order.getId(), userId, "payos", orderCode,
                order.getTotalCents(), "VND");
        payment.markPaid(Instant.now());
        paymentRepo.save(payment);

        order.setStatus("paid");
        orderRepo.save(order);

        long points = order.getTotalCents() / 100;
        pointService.creditPoints(userId, points, "payment", payment.getId(), "Deposit points from PayOS");
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String orderCode) {
        OrderEntity order = orderRepo.findByProviderOrderId(orderCode)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));

        java.util.Optional<PaymentEntity> paymentOpt = paymentRepo.findByOrderId(order.getId())
                .stream().findFirst();

        String status = order.getStatus();
        long pointsEarned = 0;
        if (paymentOpt.isPresent() && "succeeded".equals(paymentOpt.get().getStatus())) {
            pointsEarned = order.getTotalCents() / 100;
        }

        return new PaymentStatusResponse(
                orderCode,
                status,
                order.getTotalCents(),
                order.getCurrency(),
                pointsEarned,
                order.getProvider(),
                order.getProviderOrderId()
        );
    }

    @Transactional
    public void confirmPaymentByOrderCode(String orderCode) {
        OrderEntity order = orderRepo.findByProviderOrderId(orderCode)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found for code: " + orderCode));

        if (!"pending".equals(order.getStatus())) {
            return;
        }

        PaymentEntity payment = PaymentEntity.create(
                order.getId(), order.getUserId(), "payos", orderCode,
                order.getTotalCents(), "VND");
        payment.markPaid(Instant.now());
        paymentRepo.save(payment);

        order.setStatus("paid");
        orderRepo.save(order);

        long points = order.getTotalCents() / 100;
        pointService.creditPoints(order.getUserId(), points, "payment", payment.getId(), "Deposit points from PayOS");
    }
}
