package com.fuoverflow.notification.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface NotificationPreferenceRepository extends JpaRepository<NotificationPreferenceEntity, UUID> {
    Optional<NotificationPreferenceEntity> findByUserIdAndChannelAndType(
            UUID userId, String channel, String type);

    @Query("""
            SELECT p FROM NotificationPreferenceEntity p
            WHERE p.userId IN :userIds
              AND p.channel = :channel
              AND p.type = :type
              AND p.enabled = false
            """)
    List<NotificationPreferenceEntity> findDisabledAmong(
            @Param("userIds") Collection<UUID> userIds,
            @Param("channel") String channel,
            @Param("type") String type);

    @Query("""
            SELECT p FROM NotificationPreferenceEntity p
            WHERE p.userId IN :userIds
              AND p.type = :type
              AND p.enabled = false
            """)
    List<NotificationPreferenceEntity> findAllDisabledForType(
            @Param("userIds") Collection<UUID> userIds,
            @Param("type") String type);

    List<NotificationPreferenceEntity> findByUserId(UUID userId);
}
