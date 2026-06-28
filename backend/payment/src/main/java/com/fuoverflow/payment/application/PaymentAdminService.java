package com.fuoverflow.payment.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.payment.api.dto.AdminOrderDetailResponse;
import com.fuoverflow.payment.api.dto.AdminOrderListItemResponse;
import com.fuoverflow.payment.api.dto.AdminOrderPageResponse;
import com.fuoverflow.payment.api.dto.PaymentAnalyticsResponse;
import com.fuoverflow.payment.persistence.OrderEntity;
import com.fuoverflow.payment.persistence.OrderRepository;
import com.fuoverflow.payment.persistence.PaymentEntity;
import com.fuoverflow.payment.persistence.PaymentRepository;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Service
public class PaymentAdminService {

    private final OrderRepository orderRepo;
    private final PaymentRepository paymentRepo;
    private final UserRepository userRepo;

    public PaymentAdminService(OrderRepository orderRepo, PaymentRepository paymentRepo, UserRepository userRepo) {
        this.orderRepo = orderRepo;
        this.paymentRepo = paymentRepo;
        this.userRepo = userRepo;
    }

    @Transactional(readOnly = true)
    public AdminOrderPageResponse listOrders(String status, UUID userId, Instant fromDate, Instant toDate, int page, int size) {
        Page<OrderEntity> orders = orderRepo.findAllOrders(status, userId, fromDate, toDate, PageRequest.of(page, size));

        Set<UUID> userIds = new HashSet<>();
        orders.getContent().forEach(o -> userIds.add(o.getUserId()));
        Map<UUID, UserEntity> userMap = new HashMap<>();
        if (!userIds.isEmpty()) {
            userRepo.findAllById(userIds).forEach(u -> userMap.put(u.getId(), u));
        }

        List<AdminOrderListItemResponse> items = orders.getContent().stream().map(o -> {
            UserEntity user = userMap.get(o.getUserId());
            Instant paidAt = paymentRepo.findByOrderId(o.getId()).stream()
                    .filter(p -> "paid".equals(p.getStatus()))
                    .map(PaymentEntity::getPaidAt)
                    .findFirst().orElse(null);

            return new AdminOrderListItemResponse(
                    o.getId().toString(),
                    o.getProviderOrderId(),
                    o.getTotalCents(),
                    o.getCurrency(),
                    o.getStatus(),
                    o.getPointsAwarded(),
                    o.getTierLabelSnapshot(),
                    o.getUserId().toString(),
                    user != null ? user.getUsername() : null,
                    user != null ? user.getEmail() : null,
                    o.getCreatedAt(),
                    paidAt
            );
        }).toList();

        return new AdminOrderPageResponse(items, orders.getNumber(), orders.getSize(), orders.getTotalElements(), orders.getTotalPages());
    }

    @Transactional(readOnly = true)
    public AdminOrderDetailResponse getOrder(UUID orderId) {
        OrderEntity order = orderRepo.findById(orderId)
                .orElseThrow(() -> new NotFoundException("ORDER_NOT_FOUND", "Order not found"));

        UserEntity user = userRepo.findById(order.getUserId()).orElse(null);
        AdminOrderDetailResponse.UserSummary userSummary = user != null
                ? new AdminOrderDetailResponse.UserSummary(user.getId().toString(), user.getUsername(), user.getEmail(), user.getDisplayName())
                : new AdminOrderDetailResponse.UserSummary(order.getUserId().toString(), null, null, null);

        List<AdminOrderDetailResponse.PaymentRecord> payments = paymentRepo.findByOrderId(order.getId()).stream()
                .map(p -> new AdminOrderDetailResponse.PaymentRecord(
                        p.getId().toString(), p.getStatus(), p.getAmountCents(), p.getCurrency(), p.getPaidAt(), p.getCreatedAt()))
                .toList();

        return new AdminOrderDetailResponse(
                order.getId().toString(),
                order.getProviderOrderId(),
                order.getTotalCents(),
                order.getCurrency(),
                order.getStatus(),
                order.getPointsAwarded(),
                order.getTierLabelSnapshot(),
                order.getCheckoutUrl(),
                order.getExpiredAt(),
                order.getCreatedAt(),
                order.getUpdatedAt(),
                userSummary,
                payments
        );
    }

    @Transactional(readOnly = true)
    public PaymentAnalyticsResponse getAnalytics(Instant fromDate, Instant toDate) {
        List<Object[]> statusAgg = orderRepo.aggregateByStatus(fromDate, toDate);

        long totalRevenue = 0, totalTransactions = 0, totalPointsIssued = 0;
        long paid = 0, pending = 0, failed = 0, expired = 0;

        for (Object[] row : statusAgg) {
            String status = (String) row[0];
            long count = (Long) row[1];
            long revenue = (Long) row[2];
            long points = (Long) row[3];
            totalTransactions += count;

            switch (status) {
                case "paid" -> { paid = count; totalRevenue = revenue; totalPointsIssued = points; }
                case "pending" -> pending = count;
                case "failed" -> failed = count;
                case "expired" -> expired = count;
            }
        }

        double conversionRate = totalTransactions > 0 ? (double) paid / totalTransactions : 0;
        long averageDeposit = paid > 0 ? totalRevenue / paid : 0;

        List<Object[]> tierRows = orderRepo.revenueByTier(fromDate, toDate);
        List<PaymentAnalyticsResponse.TierRevenue> revenueByTier = tierRows.stream()
                .map(r -> new PaymentAnalyticsResponse.TierRevenue(
                        (String) r[0], (Long) r[1], (Long) r[2]))
                .toList();

        List<Object[]> topRows = orderRepo.topUsersByRevenue(fromDate, toDate, PageRequest.of(0, 10));
        Set<UUID> topUserIds = new HashSet<>();
        topRows.forEach(r -> topUserIds.add((UUID) r[0]));
        Map<UUID, UserEntity> topUserMap = new HashMap<>();
        if (!topUserIds.isEmpty()) {
            userRepo.findAllById(topUserIds).forEach(u -> topUserMap.put(u.getId(), u));
        }

        List<PaymentAnalyticsResponse.TopUser> topUsers = topRows.stream()
                .map(r -> {
                    UUID uid = (UUID) r[0];
                    UserEntity u = topUserMap.get(uid);
                    return new PaymentAnalyticsResponse.TopUser(
                            uid.toString(),
                            u != null ? u.getUsername() : null,
                            (Long) r[2],
                            (Long) r[1]
                    );
                }).toList();

        return new PaymentAnalyticsResponse(
                totalRevenue, totalTransactions, totalPointsIssued,
                new PaymentAnalyticsResponse.StatusBreakdown(paid, pending, failed, expired),
                conversionRate, averageDeposit, revenueByTier, topUsers
        );
    }
}
