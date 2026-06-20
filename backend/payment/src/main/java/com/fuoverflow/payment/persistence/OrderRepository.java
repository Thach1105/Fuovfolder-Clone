package com.fuoverflow.payment.persistence;

import com.fuoverflow.payment.persistence.OrderEntity;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface OrderRepository extends JpaRepository<OrderEntity, UUID> {
    Optional<OrderEntity> findByProviderAndProviderOrderId(String provider, String providerOrderId);

    Optional<OrderEntity> findByIdempotencyKey(String idempotencyKey);

    Optional<OrderEntity> findByProviderOrderId(String providerOrderId);
}
