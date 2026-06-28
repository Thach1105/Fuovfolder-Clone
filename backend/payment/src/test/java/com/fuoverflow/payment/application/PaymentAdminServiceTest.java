package com.fuoverflow.payment.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.payment.api.dto.AdminOrderDetailResponse;
import com.fuoverflow.payment.api.dto.AdminOrderPageResponse;
import com.fuoverflow.payment.api.dto.PaymentAnalyticsResponse;
import com.fuoverflow.payment.persistence.OrderEntity;
import com.fuoverflow.payment.persistence.OrderRepository;
import com.fuoverflow.payment.persistence.PaymentEntity;
import com.fuoverflow.payment.persistence.PaymentRepository;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentAdminServiceTest {

    @Mock
    private OrderRepository orderRepo;

    @Mock
    private PaymentRepository paymentRepo;

    @Mock
    private UserRepository userRepo;

    @InjectMocks
    private PaymentAdminService service;

    @Test
    void listOrders_shouldReturnPaginatedOrdersWithUserInfo() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 100000, "VND", "payos", "order-1");
        order.setPointsAwarded(100000);
        order.setTierLabelSnapshot("Nạp 100K");

        Page<OrderEntity> page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
        when(orderRepo.findAllOrders(eq(null), eq(null), eq(null), eq(null), any())).thenReturn(page);
        when(userRepo.findAllById(any())).thenReturn(Collections.emptyList());
        when(paymentRepo.findByOrderId(order.getId())).thenReturn(Collections.emptyList());

        AdminOrderPageResponse response = service.listOrders(null, null, null, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().orderCode()).isEqualTo("order-1");
        assertThat(response.items().getFirst().amount()).isEqualTo(100000);
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void listOrders_shouldReturnEmptyWhenNoOrders() {
        Page<OrderEntity> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(orderRepo.findAllOrders(any(), any(), any(), any(), any())).thenReturn(emptyPage);

        AdminOrderPageResponse response = service.listOrders(null, null, null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void getOrder_shouldReturnDetailWithPayments() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 100000, "VND", "payos", "order-1");
        order.setPointsAwarded(100000);
        order.setTierLabelSnapshot("Nạp 100K");
        order.setCheckoutUrl("https://pay.payos.vn/checkout/abc");
        order.setExpiredAt(Instant.now().plusSeconds(1800));

        PaymentEntity payment = PaymentEntity.create(order.getId(), userId, "payos", "order-1", 100000, "VND");
        payment.markPaid(Instant.now());

        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));
        when(userRepo.findById(userId)).thenReturn(Optional.empty());
        when(paymentRepo.findByOrderId(order.getId())).thenReturn(List.of(payment));

        AdminOrderDetailResponse response = service.getOrder(order.getId());

        assertThat(response.orderCode()).isEqualTo("order-1");
        assertThat(response.amount()).isEqualTo(100000);
        assertThat(response.payments()).hasSize(1);
        assertThat(response.payments().getFirst().status()).isEqualTo("paid");
        assertThat(response.checkoutUrl()).isEqualTo("https://pay.payos.vn/checkout/abc");
    }

    @Test
    void getOrder_shouldThrowWhenNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.getOrder(orderId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getAnalytics_shouldAggregateStatusBreakdown() {
        List<Object[]> statusAgg = List.of(
                new Object[]{"paid", 10L, 500000L, 550000L},
                new Object[]{"pending", 3L, 0L, 0L},
                new Object[]{"failed", 2L, 0L, 0L}
        );
        when(orderRepo.aggregateByStatus(any(), any())).thenReturn(statusAgg);
        when(orderRepo.revenueByTier(any(), any())).thenReturn(Collections.emptyList());
        when(orderRepo.topUsersByRevenue(any(), any(), any())).thenReturn(Collections.emptyList());

        PaymentAnalyticsResponse response = service.getAnalytics(null, null);

        assertThat(response.totalRevenue()).isEqualTo(500000);
        assertThat(response.totalTransactions()).isEqualTo(15);
        assertThat(response.totalPointsIssued()).isEqualTo(550000);
        assertThat(response.statusBreakdown().paid()).isEqualTo(10);
        assertThat(response.statusBreakdown().pending()).isEqualTo(3);
        assertThat(response.statusBreakdown().failed()).isEqualTo(2);
        assertThat(response.statusBreakdown().expired()).isZero();
        assertThat(response.conversionRate()).isCloseTo(0.6667, org.assertj.core.data.Offset.offset(0.001));
        assertThat(response.averageDepositAmount()).isEqualTo(50000);
    }

    @Test
    void getAnalytics_shouldReturnZerosWhenNoData() {
        when(orderRepo.aggregateByStatus(any(), any())).thenReturn(Collections.emptyList());
        when(orderRepo.revenueByTier(any(), any())).thenReturn(Collections.emptyList());
        when(orderRepo.topUsersByRevenue(any(), any(), any())).thenReturn(Collections.emptyList());

        PaymentAnalyticsResponse response = service.getAnalytics(null, null);

        assertThat(response.totalRevenue()).isZero();
        assertThat(response.totalTransactions()).isZero();
        assertThat(response.conversionRate()).isZero();
    }

    @Test
    void getAnalytics_shouldReturnRevenueByTier() {
        when(orderRepo.aggregateByStatus(any(), any())).thenReturn(Collections.emptyList());
        when(orderRepo.revenueByTier(any(), any())).thenReturn(List.of(
                new Object[]{"Nạp 100K", 5L, 500000L},
                new Object[]{"Nạp 50K", 3L, 150000L}
        ));
        when(orderRepo.topUsersByRevenue(any(), any(), any())).thenReturn(Collections.emptyList());

        PaymentAnalyticsResponse response = service.getAnalytics(null, null);

        assertThat(response.revenueByTier()).hasSize(2);
        assertThat(response.revenueByTier().getFirst().tierLabel()).isEqualTo("Nạp 100K");
        assertThat(response.revenueByTier().getFirst().totalRevenue()).isEqualTo(500000);
    }
}
