package com.fuoverflow.payment.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.deposit.persistence.DepositTierEntity;
import com.fuoverflow.deposit.persistence.DepositTierRepository;
import com.fuoverflow.payment.api.dto.PayOSPaymentLinkResponse;
import com.fuoverflow.payment.api.dto.PaymentStatusResponse;
import com.fuoverflow.payment.persistence.OrderEntity;
import com.fuoverflow.payment.persistence.OrderRepository;
import com.fuoverflow.payment.persistence.PaymentEntity;
import com.fuoverflow.payment.persistence.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkRequest;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentService {

    private static final Logger log = LoggerFactory.getLogger(PaymentService.class);

    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final PayOS payOS;
    private final PointService pointService;
    private final DepositTierRepository tierRepo;

    public PaymentService(OrderRepository orderRepo, PaymentRepository paymentRepo,
                          PayOS payOS, PointService pointService, DepositTierRepository tierRepo) {
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.payOS = payOS;
        this.pointService = pointService;
        this.tierRepo = tierRepo;
    }

    @Transactional
    public PayOSPaymentLinkResponse createPaymentLink(UUID tierId, String returnUrl, String cancelUrl, UUID userId) {
        DepositTierEntity tier = tierRepo.findById(tierId)
                .filter(DepositTierEntity::isActive)
                .orElseThrow(() -> new NotFoundException("DEPOSIT_TIER_NOT_FOUND", "Deposit tier not found: " + tierId));

        String idempotencyKey = buildIdempotencyKey(userId, tierId, returnUrl, cancelUrl);
        Optional<OrderEntity> existing = orderRepo.findByIdempotencyKey(idempotencyKey);
        if (existing.isPresent()) {
            return reuseExistingLink(existing.get());
        }

        long orderCode = generateOrderCode();
        CreatePaymentLinkRequest request = CreatePaymentLinkRequest.builder()
                .orderCode(orderCode)
                .amount((long) tier.getAmountVnd())
                .description("Nap diem " + tier.getLabel())
                .returnUrl(returnUrl)
                .cancelUrl(cancelUrl)
                .build();

        CreatePaymentLinkResponse response;
        try {
            response = payOS.paymentRequests().create(request);
        } catch (Exception ex) {
            log.error("Failed to create PayOS payment link: userId={}, tierId={}, amountVnd={}, returnUrl={}, cancelUrl={}",
                    userId, tierId, tier.getAmountVnd(), returnUrl, cancelUrl, ex);
            throw ex;
        }

        try {
            OrderEntity order = OrderEntity.createFromTier(userId, tier.getAmountVnd(), tier, "payos", String.valueOf(orderCode));
            order.setIdempotencyKey(idempotencyKey);
            orderRepo.save(order);
            return new PayOSPaymentLinkResponse(response.getCheckoutUrl(), response.getQrCode(), String.valueOf(orderCode));
        } catch (DataIntegrityViolationException ex) {
            OrderEntity persisted = orderRepo.findByIdempotencyKey(idempotencyKey).orElseThrow(() -> ex);
            return reuseExistingLink(persisted);
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

    static long generateOrderCode() {
        return Math.abs(UUID.randomUUID().getMostSignificantBits());
    }

    String buildIdempotencyKey(UUID userId, UUID tierId, String returnUrl, String cancelUrl) {
        String raw = userId + "|" + tierId + "|" + returnUrl + "|" + cancelUrl;
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
        long points;
        if (order.getPointsAwarded() != null) {
            points = order.getPointsAwarded();
        } else {
            points = order.getTotalCents() / 100;
        }
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
                    order.getTotalCents(), order.getCurrency());
            payment.markPaid(Instant.now());
            return paymentRepo.save(payment);
        } catch (DataIntegrityViolationException ex) {
            return paymentRepo.findByProviderAndProviderPaymentId("payos", orderCode).orElseThrow(() -> ex);
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
            if (order.getPointsAwarded() != null) {
                return order.getPointsAwarded();
            }
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
