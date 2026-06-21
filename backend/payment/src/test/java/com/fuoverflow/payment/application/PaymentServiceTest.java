package com.fuoverflow.payment.application;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.deposit.persistence.DepositTierEntity;
import com.fuoverflow.deposit.persistence.DepositTierRepository;
import com.fuoverflow.payment.api.dto.PaymentStatusResponse;
import com.fuoverflow.payment.persistence.OrderEntity;
import com.fuoverflow.payment.persistence.OrderRepository;
import com.fuoverflow.payment.persistence.PaymentEntity;
import com.fuoverflow.payment.persistence.PaymentRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Answers;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.slf4j.LoggerFactory;
import vn.payos.PayOS;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.tuple;

import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
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
    private PointService pointService;

    @Mock
    private DepositTierRepository tierRepo;

    @InjectMocks
    private PaymentService paymentService;

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
            when(orderRepo.findByIdempotencyKey(any())).thenReturn(Optional.empty());
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
    void confirmPayment_shouldRejectWhenOrderBelongsToAnotherUser() {
        UUID ownerId = UUID.randomUUID();
        UUID anotherUserId = UUID.randomUUID();
        OrderEntity order = OrderEntity.create(ownerId, 10000, "VND", "payos", "order-1");
        when(orderRepo.findByProviderOrderId("order-1")).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> paymentService.confirmPayment("order-1", anotherUserId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Order does not belong to user");

        verify(paymentRepo, never()).save(any(PaymentEntity.class));
        verify(pointService, never()).creditPoints(any(UUID.class), any(Long.class), any(String.class), any(UUID.class), any(String.class));
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
        verify(pointService, never()).creditPoints(any(UUID.class), any(Long.class), any(String.class), any(UUID.class), any(String.class));
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
        verify(pointService, times(1))
                .creditPoints(userId, 100L, "payment", savedPayment.getId(), "Deposit points from PayOS");
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
    void generateOrderCode_shouldReturnPositiveValue() {
        long orderCode = PaymentService.generateOrderCode();

        assertThat(orderCode).isPositive();
    }
}
