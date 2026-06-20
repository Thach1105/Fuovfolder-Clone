package com.fuoverflow.payment.persistence;

import com.fuoverflow.payment.persistence.PaymentEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentRepository extends JpaRepository<PaymentEntity, UUID> {
    Optional<PaymentEntity> findByProviderAndProviderPaymentId(String provider, String providerPaymentId);

    java.util.List<PaymentEntity> findByUserIdAndStatus(UUID userId, String status);

    java.util.List<PaymentEntity> findByOrderId(UUID orderId);
}
