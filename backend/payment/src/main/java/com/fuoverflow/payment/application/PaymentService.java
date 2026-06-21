package com.fuoverflow.payment.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.payment.api.dto.PayOSPaymentLinkResponse;
import com.fuoverflow.payment.api.dto.PaymentStatusResponse;
import com.fuoverflow.payment.persistence.OrderEntity;
import com.fuoverflow.payment.persistence.OrderRepository;
import com.fuoverflow.payment.persistence.PaymentEntity;
import com.fuoverflow.payment.persistence.PaymentRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
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
            String idempotencyKey = buildIdempotencyKey(userId, amount, returnUrl, cancelUrl, description);
            Optional<OrderEntity> existingOrder = orderRepo.findByIdempotencyKey(idempotencyKey);
            if (existingOrder.isPresent()) {
                return reuseExistingLink(existingOrder.get());
            }

            long orderCode = generateOrderCode();
            int totalAmountVnd = amount.intValueExact();

            CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                    .orderCode(orderCode)
                    .amount(amount.longValueExact())
                    .description(description)
                    .returnUrl(returnUrl)
                    .cancelUrl(cancelUrl)
                    .build();

            CreatePaymentLinkResponse response = payOS.paymentRequests().create(request);

            try {
                OrderEntity order = OrderEntity.create(userId, totalAmountVnd, "VND", "payos", String.valueOf(orderCode));
                order.setIdempotencyKey(idempotencyKey);
                orderRepo.save(order);
            } catch (DataIntegrityViolationException ex) {
                return reuseExistingLink(
                        orderRepo.findByIdempotencyKey(idempotencyKey)
                                .orElseThrow(() -> ex)
                );
            }

            return new PayOSPaymentLinkResponse(
                    response.getCheckoutUrl(),
                    response.getQrCode(),
                    String.valueOf(orderCode)
            );
        } catch (Exception e) {
            throw new RuntimeException("Failed to create payment link: " + e.getMessage(), e);
        }
    }

    private PayOSPaymentLinkResponse reuseExistingLink(OrderEntity existingOrder) {
        String orderCode = existingOrder.getProviderOrderId();
        try {
            payOS.paymentRequests().get(Long.parseLong(orderCode));
        } catch (Exception lookupFailure) {
            log.warn("Failed to rehydrate payment link from provider for orderCode={}", orderCode, lookupFailure);
        }
        return new PayOSPaymentLinkResponse(null, null, orderCode);
    }

    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(PaymentService.class);

    static long generateOrderCode() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }

    String buildIdempotencyKey(UUID userId, BigDecimal amount, String returnUrl, String cancelUrl, String description) {
        String raw = userId + "|" + amount.toPlainString() + "|" + returnUrl + "|" + cancelUrl + "|"
                + (description == null ? "" : description);
        return UUID.nameUUIDFromBytes(raw.getBytes(StandardCharsets.UTF_8)).toString();
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
        try {
            PaymentEntity payment = PaymentEntity.create(
                    order.getId(), order.getUserId(), "payos", orderCode,
                    order.getTotalCents(), "VND");
            payment.markPaid(Instant.now());
            return paymentRepo.save(payment);
        } catch (DataIntegrityViolationException ex) {
            return paymentRepo.findByProviderAndProviderPaymentId("payos", orderCode)
                    .orElseThrow(() -> ex);
        }
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
        if (paymentOpt.isPresent() && "paid".equals(paymentOpt.get().getStatus())) {
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
