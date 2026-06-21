package com.fuoverflow.payment.application;

import com.fuoverflow.payment.persistence.PaymentEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

class PaymentStatusNormalizationTest {

    @Test
    void markPaid_shouldSetStatusToPaid() {
        PaymentEntity payment = PaymentEntity.create(
                UUID.randomUUID(),
                UUID.randomUUID(),
                "payos",
                "order-1",
                1000,
                "VND"
        );

        payment.markPaid(Instant.now());

        assertThat(payment.getStatus()).isEqualTo("paid");
    }
}
