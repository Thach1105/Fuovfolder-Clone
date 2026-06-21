package com.fuoverflow.payment.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PaymentWebhookEventRepository extends JpaRepository<PaymentWebhookEventEntity, UUID> {
    Optional<PaymentWebhookEventEntity> findByProviderAndProviderEventId(String provider, String providerEventId);
}
