package com.fuoverflow.payment.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fuoverflow.award.application.PointsWalletService;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.deposit.persistence.DepositTierEntity;
import com.fuoverflow.deposit.persistence.DepositTierRepository;
import com.fuoverflow.payment.api.dto.DepositHistoryPageResponse;
import com.fuoverflow.payment.api.dto.DepositResumeResponse;
import com.fuoverflow.payment.api.dto.PaymentStatusResponse;
import com.fuoverflow.payment.config.PaymentProperties;
import com.fuoverflow.payment.persistence.OrderEntity;
import com.fuoverflow.payment.persistence.OrderRepository;
import com.fuoverflow.payment.persistence.PaymentEntity;
import com.fuoverflow.payment.persistence.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import vn.payos.PayOS;
import vn.payos.model.v2.paymentRequests.CreatePaymentLinkResponse;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaymentServiceTest {

    @Mock
    private OrderRepository orderRepo;

    @Mock
    private PaymentRepository paymentRepo;

    @Mock(answer = Answers.RETURNS_DEEP_STUBS)
    private PayOS payOS;

    @Mock
    private PointsWalletService pointsWalletService;

    @Mock
    private DepositTierRepository tierRepo;

    @Mock
    private org.springframework.context.ApplicationEventPublisher eventPublisher;

    @Mock
    private com.fuoverflow.common.broadcast.UserDisplayNameLookup userDisplayNameLookup;

    private PaymentProperties paymentProperties;

    private PaymentService paymentService;

    @BeforeEach
    void setUp() {
        paymentProperties = new PaymentProperties(30);
        paymentService = new PaymentService(orderRepo, paymentRepo, payOS, pointsWalletService, tierRepo, paymentProperties, eventPublisher, userDisplayNameLookup);
    }

    @Test
    void createPaymentLink_shouldLogContextWhenPayOSCreateFails() {
        UUID userId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        DepositTierEntity tier = DepositTierEntity.create("Nap 10k", 10000, 10000, 0, true, 0, Instant.now());
        RuntimeException providerFailure = new RuntimeException("payos boom");
        Logger logger = (Logger) LoggerFactory.getLogger(PaymentService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            when(tierRepo.findById(tierId)).thenReturn(Optional.of(tier));
            when(payOS.paymentRequests().create(any())).thenThrow(providerFailure);

            assertThatThrownBy(() -> paymentService.createPaymentLink(
                    tierId,
                    "https://fuexam.com/payment/success",
                    "https://fuexam.com/payment/cancel",
                    userId
            )).isSameAs(providerFailure);

            List<ILoggingEvent> events = appender.list;
            assertThat(events)
                    .extracting(ILoggingEvent::getFormattedMessage, ILoggingEvent::getThrowableProxy)
                    .extracting(tuple -> tuple)
                    .isNotEmpty();
            assertThat(events.getLast().getFormattedMessage())
                    .contains("Failed to create PayOS payment link")
                    .contains(tierId.toString())
                    .contains(userId.toString());
            assertThat(events.getLast().getThrowableProxy().getMessage()).contains("payos boom");
        } finally {
            logger.detachAppender(appender);
        }
    }


    @Test
    void createPaymentLink_shouldCreateFreshProviderLinkEvenWhenSameTierWasRequestedBefore() {
        UUID userId = UUID.randomUUID();
        UUID tierId = UUID.randomUUID();
        DepositTierEntity tier = DepositTierEntity.create("Nap 10k", 10000, 10000, 0, true, 0, Instant.now());
        CreatePaymentLinkResponse response = org.mockito.Mockito.mock(CreatePaymentLinkResponse.class);

        when(tierRepo.findById(tierId)).thenReturn(Optional.of(tier));
        when(payOS.paymentRequests().create(any())).thenReturn(response);
        when(response.getCheckoutUrl()).thenReturn("https://pay.payos.vn/checkout/abc");
        when(response.getQrCode()).thenReturn("qr-abc");

        paymentService.createPaymentLink(tierId, "https://fuexam.com/payment/success", "https://fuexam.com/payment/cancel", userId);
        var second = paymentService.createPaymentLink(tierId, "https://fuexam.com/payment/success", "https://fuexam.com/payment/cancel", userId);

        assertThat(second.checkoutUrl()).isEqualTo("https://pay.payos.vn/checkout/abc");
        assertThat(second.qrCode()).isEqualTo("qr-abc");
        verify(payOS.paymentRequests(), times(2)).create(any());
    }

    @Test
    void confirmPayment_shouldRejectWhenOrderBelongsToAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(ownerId, 10000, "VND", "payos", "order-1");
        when(orderRepo.findByProviderOrderId("order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.confirmPayment("order-1", anotherUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order does not belong to user");

        verify(paymentRepo, never()).save(any(PaymentEntity.class));
        verify(pointsWalletService, never()).credit(any(UUID.class), anyInt(), any(String.class), any(String.class), any(UUID.class));
    }

    @Test
    void confirmPaymentByOrderCode_shouldSkipWhenPaymentAlreadyExists() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 10000, "VND", "payos", "order-1");
        PaymentEntity payment = PaymentEntity.create(order.getId(), userId, "payos", "order-1", 10000, "VND");
        payment.markPaid(Instant.now());

        when(orderRepo.findByProviderOrderId("order-1")).thenReturn(Optional.of(order));
        when(paymentRepo.findByProviderAndProviderPaymentId("payos", "order-1")).thenReturn(Optional.of(payment));

        paymentService.confirmPaymentByOrderCode("order-1");

        verify(paymentRepo, never()).save(any(PaymentEntity.class));
        verify(pointsWalletService, never()).credit(any(UUID.class), anyInt(), any(String.class), any(String.class), any(UUID.class));
        verify(orderRepo, times(1)).save(order);
    }

    @Test
    void confirmPaymentByOrderCode_shouldCreatePaymentAndCreditPointsWhenPending() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 10000, "VND", "payos", "order-1");
        PaymentEntity savedPayment = PaymentEntity.create(order.getId(), userId, "payos", "order-1", 10000, "VND");

        when(orderRepo.findByProviderOrderId("order-1")).thenReturn(Optional.of(order));
        when(paymentRepo.findByProviderAndProviderPaymentId("payos", "order-1")).thenReturn(Optional.empty());
        when(paymentRepo.save(any(PaymentEntity.class))).thenReturn(savedPayment);

        paymentService.confirmPaymentByOrderCode("order-1");

        verify(paymentRepo, times(1)).save(any(PaymentEntity.class));
        verify(pointsWalletService, times(1))
                .credit(userId, 100, "Deposit points from PayOS", PointsWalletService.SOURCE_TOPUP, savedPayment.getId());
        verify(orderRepo, times(1)).save(order);
    }

    @Test
    void confirmPaymentByOrderCode_shouldThrowWhenOrderMissing() {
        when(orderRepo.findByProviderOrderId("missing")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.confirmPaymentByOrderCode("missing"))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getPaymentStatus_shouldRejectWhenOrderBelongsToAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(ownerId, 10000, "VND", "payos", "order-1");
        when(orderRepo.findByProviderOrderId("order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.getPaymentStatus("order-1", anotherUserId, false))
                .isInstanceOf(NotFoundException.class)
                .hasMessage("Order not found");
    }

    @Test
    void getPaymentStatus_shouldAllowAdminToViewAnotherUsersOrder() {
        UUID ownerId = UUID.randomUUID();
        UUID adminUserId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(ownerId, 10000, "VND", "payos", "order-1");
        when(orderRepo.findByProviderOrderId("order-1")).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderId(order.getId())).thenReturn(java.util.List.of());

        PaymentStatusResponse response = paymentService.getPaymentStatus("order-1", adminUserId, true);

        org.assertj.core.api.Assertions.assertThat(response.providerOrderId()).isEqualTo("order-1");
    }

    @Test
    void getPaymentStatus_shouldReturnProviderOrderIdForOwner() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 10000, "VND", "payos", "order-1");
        PaymentEntity payment = PaymentEntity.create(order.getId(), userId, "payos", "order-1", 10000, "VND");
        payment.markPaid(Instant.now());

        when(orderRepo.findByProviderOrderId("order-1")).thenReturn(Optional.of(order));
        when(paymentRepo.findByOrderId(order.getId())).thenReturn(java.util.List.of(payment));

        PaymentStatusResponse response = paymentService.getPaymentStatus("order-1", userId, false);

        assertThat(response.providerOrderId()).isEqualTo("order-1");
    }

    // createPaymentLink test moved/removed: the public API was changed from
    // createPaymentLink(BigDecimal, String, String, String, UUID) to
    // createPaymentLink(UUID tierId, String, String, UUID), which requires a
    // DepositTierRepository mock this test class doesn't set up. Coverage for
    // the new flow is provided by the deposit/payment integration test (Task 13).

    @Test
    void generateOrderCode_shouldStayWithinPayOSSafeIntegerRange() {
        for (int i = 0; i < 20; i++) {
            long orderCode = PaymentService.generateOrderCode();
            assertThat(orderCode).isPositive();
            assertThat(orderCode).isLessThanOrEqualTo(9_007_199_254_740_991L);
        }
    }

    // --- getResumeInfo tests ---

    @Test
    void getResumeInfo_shouldReturnResumableWhenPendingAndNotExpired() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 10000, "VND", "payos", "order-1");
        order.setCheckoutUrl("https://pay.payos.vn/checkout/abc");
        order.setExpiredAt(Instant.now().plus(15, ChronoUnit.MINUTES));
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        DepositResumeResponse response = paymentService.getResumeInfo(order.getId(), userId);

        assertThat(response.canResume()).isTrue();
        assertThat(response.checkoutUrl()).isEqualTo("https://pay.payos.vn/checkout/abc");
        assertThat(response.remainingSeconds()).isPositive();
        assertThat(response.reason()).isNull();
    }

    @Test
    void getResumeInfo_shouldReturnExpiredAndUpdateStatusWhenPastExpiry() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 10000, "VND", "payos", "order-1");
        order.setCheckoutUrl("https://pay.payos.vn/checkout/abc");
        order.setExpiredAt(Instant.now().minus(5, ChronoUnit.MINUTES));
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        DepositResumeResponse response = paymentService.getResumeInfo(order.getId(), userId);

        assertThat(response.canResume()).isFalse();
        assertThat(response.reason()).isEqualTo("EXPIRED");
        assertThat(order.getStatus()).isEqualTo("expired");
        verify(orderRepo).save(order);
    }

    @Test
    void getResumeInfo_shouldReturnExpiredWhenCheckoutUrlIsNull() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 10000, "VND", "payos", "order-1");
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        DepositResumeResponse response = paymentService.getResumeInfo(order.getId(), userId);

        assertThat(response.canResume()).isFalse();
        assertThat(response.reason()).isEqualTo("EXPIRED");
        verify(orderRepo).save(order);
    }

    @Test
    void getResumeInfo_shouldReturnAlreadyPaidWhenOrderIsPaid() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 10000, "VND", "payos", "order-1");
        order.markAsPaid();
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        DepositResumeResponse response = paymentService.getResumeInfo(order.getId(), userId);

        assertThat(response.canResume()).isFalse();
        assertThat(response.reason()).isEqualTo("ALREADY_PAID");
        verify(orderRepo, never()).save(any());
    }

    @Test
    void getResumeInfo_shouldRejectWhenOrderBelongsToAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(ownerId, 10000, "VND", "payos", "order-1");
        when(orderRepo.findById(order.getId())).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.getResumeInfo(order.getId(), anotherUserId))
                .isInstanceOf(NotFoundException.class);
    }

    @Test
    void getResumeInfo_shouldThrowWhenOrderNotFound() {
        UUID orderId = UUID.randomUUID();
        when(orderRepo.findById(orderId)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> paymentService.getResumeInfo(orderId, UUID.randomUUID()))
                .isInstanceOf(NotFoundException.class);
    }

    // --- listUserDeposits tests ---

    @Test
    void listUserDeposits_shouldReturnPaginatedResults() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 50000, "VND", "payos", "order-1");
        order.setPointsAwarded(50000);
        order.setTierLabelSnapshot("Nạp 50K");
        order.setExpiredAt(Instant.now().plus(10, ChronoUnit.MINUTES));
        order.setCheckoutUrl("https://pay.payos.vn/checkout/xyz");

        Page<OrderEntity> page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
        when(orderRepo.findUserDeposits(eq(userId), eq(null), eq(null), eq(null), any())).thenReturn(page);
        when(paymentRepo.findByOrderId(order.getId())).thenReturn(Collections.emptyList());

        DepositHistoryPageResponse response = paymentService.listUserDeposits(userId, null, null, null, 0, 20);

        assertThat(response.items()).hasSize(1);
        assertThat(response.items().getFirst().orderCode()).isEqualTo("order-1");
        assertThat(response.items().getFirst().canResume()).isTrue();
        assertThat(response.totalElements()).isEqualTo(1);
    }

    @Test
    void listUserDeposits_shouldReturnEmptyWhenNoOrders() {
        UUID userId = UUID.randomUUID();
        Page<OrderEntity> emptyPage = new PageImpl<>(Collections.emptyList(), PageRequest.of(0, 20), 0);
        when(orderRepo.findUserDeposits(eq(userId), any(), any(), any(), any())).thenReturn(emptyPage);

        DepositHistoryPageResponse response = paymentService.listUserDeposits(userId, null, null, null, 0, 20);

        assertThat(response.items()).isEmpty();
        assertThat(response.totalElements()).isZero();
    }

    @Test
    void listUserDeposits_shouldMarkCanResumeFalseWhenExpired() {
        UUID userId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(userId, 50000, "VND", "payos", "order-1");
        order.setCheckoutUrl("https://pay.payos.vn/checkout/xyz");
        order.setExpiredAt(Instant.now().minus(5, ChronoUnit.MINUTES));

        Page<OrderEntity> page = new PageImpl<>(List.of(order), PageRequest.of(0, 20), 1);
        when(orderRepo.findUserDeposits(eq(userId), any(), any(), any(), any())).thenReturn(page);
        when(paymentRepo.findByOrderId(order.getId())).thenReturn(Collections.emptyList());

        DepositHistoryPageResponse response = paymentService.listUserDeposits(userId, null, null, null, 0, 20);

        assertThat(response.items().getFirst().canResume()).isFalse();
    }
}
