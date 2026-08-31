package com.fuoverflow.exam.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ExamWebhookEventRepository extends JpaRepository<ExamWebhookEventEntity, UUID> {

    Optional<ExamWebhookEventEntity> findByClientIdAndEventId(String clientId, String eventId);

    List<ExamWebhookEventEntity> findTop20ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc(
            String status, Instant before);

    List<ExamWebhookEventEntity> findTop50ByStatusOrderByCreatedAtDesc(String status);

    List<ExamWebhookEventEntity> findTop50ByOrderByCreatedAtDesc();
}
