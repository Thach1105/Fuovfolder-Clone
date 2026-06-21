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
import java.util.Optional;
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
            int totalAmountVnd = amount.intValueExact();

            OrderEntity order = OrderEntity.create(userId,
                    totalAmountVnd,
                    "VND", "payos", String.valueOf(orderCode));
            order.setIdempotencyKey(idempotencyKey);
            orderRepo.save(order);

            CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amount.longValueExact())
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

        if (!order.getUserId().equals(userId)) {
            throw new IllegalArgumentException("Order does not belong to user");
        }

        confirmPaymentByOrderCode(orderCode);
    }

    private void creditPointsForPaidOrder(OrderEntity order, PaymentEntity payment) {
        long points = order.getTotalCents() / 100;
        pointService.creditPoints(order.getUserId(), points, "payment", payment.getId(), "Deposit points from PayOS");
    }

    private Optional<PaymentEntity> findExistingPayment(String orderCode) {
        return paymentRepo.findByProviderAndProviderPaymentId("payos", orderCode);
    }

    private void markOrderPaid(OrderEntity order) {
        order.markAsPaid();
        orderRepo.save(order);
    }

    private PaymentEntity createPaidPayment(OrderEntity order, String orderCode) {
        PaymentEntity payment = PaymentEntity.create(
                order.getId(), order.getUserId(), "payos", orderCode,
                order.getTotalCents(), "VND");
        payment.markPaid(Instant.now());
        return paymentRepo.save(payment);
    }

    private void confirmPendingOrder(OrderEntity order, String orderCode) {
        PaymentEntity payment = createPaidPayment(order, orderCode);
        markOrderPaid(order);
        creditPointsForPaidOrder(order, payment);
    }

    private void confirmExistingPayment(OrderEntity order) {
        markOrderPaid(order);
    }

    private void confirmOrder(OrderEntity order, String orderCode) {
        if (!"pending".equals(order.getStatus())) {
            return;
        }

        if (findExistingPayment(orderCode).isPresent()) {
            confirmExistingPayment(order);
            return;
        }

        confirmPendingOrder(order, orderCode);
    }

    private OrderEntity getOrderByCode(String orderCode) {
        return orderRepo.findByProviderOrderId(orderCode)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found for code: " + orderCode));
    }

    private OrderEntity getOrderForStatus(String orderCode, UUID userId, boolean isAdmin) {
        OrderEntity order = orderRepo.findByProviderOrderId(orderCode)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));

        if (!isAdmin && !order.getUserId().equals(userId)) {
            throw new NotFoundException("ORDER_NOT_FOUND", "Order not found");
        }

        return order;
    }

    private Optional<PaymentEntity> getPaymentForOrder(OrderEntity order) {
        return paymentRepo.findByOrderId(order.getId()).stream().findFirst();
    }

    private long calculatePointsEarned(OrderEntity order, Optional<PaymentEntity> paymentOpt) {
        if (paymentOpt.isPresent() && "succeeded".equals(paymentOpt.get().getStatus())) {
            return order.getTotalCents() / 100;
        }
        return 0;
    }

    private PaymentStatusResponse toPaymentStatusResponse(String orderCode, OrderEntity order, long pointsEarned) {
        return new PaymentStatusResponse(
                orderCode,
                order.getStatus(),
                order.getTotalCents(),
                order.getCurrency(),
                pointsEarned,
                order.getProvider(),
                order.getProviderOrderId()
        );
    }

    @Transactional(readOnly = true)
    public PaymentStatusResponse getPaymentStatus(String orderCode, UUID userId, boolean isAdmin) {
        OrderEntity order = getOrderForStatus(orderCode, userId, isAdmin);
        Optional<PaymentEntity> paymentOpt = getPaymentForOrder(order);
        long pointsEarned = calculatePointsEarned(order, paymentOpt);
        return toPaymentStatusResponse(orderCode, order, pointsEarned);
    }

    @Transactional
    public void confirmPaymentByOrderCode(String orderCode) {
        OrderEntity order = getOrderByCode(orderCode);
        confirmOrder(order, orderCode);
    }

}
