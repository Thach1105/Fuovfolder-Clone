package com.fuoverflow.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushSubscriptionRepository extends JpaRepository<PushSubscriptionEntity, UUID> {
    Optional<PushSubscriptionEntity> findByEndpoint(String endpoint);

    @Query("""
            SELECT p FROM PushSubscriptionEntity p
            WHERE p.userId IN :userIds
            """)
    List<PushSubscriptionEntity> findByUserIdIn(@Param("userIds") Collection<UUID> userIds);

    void deleteByEndpoint(String endpoint);

    long countByUserId(UUID userId);
}
