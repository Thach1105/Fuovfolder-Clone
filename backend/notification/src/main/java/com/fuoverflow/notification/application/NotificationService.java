package com.fuoverflow.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.notification.api.dto.NotificationPageResponse;
import com.fuoverflow.notification.api.dto.NotificationResponse;
import com.fuoverflow.notification.api.dto.UnreadCountResponse;
import com.fuoverflow.notification.domain.NotificationTypes;
import com.fuoverflow.notification.domain.ThreadReplyNotificationPayload;
import com.fuoverflow.notification.persistence.NotificationEntity;
import com.fuoverflow.notification.persistence.NotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class NotificationService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;
    private static final int BATCH_CHUNK_SIZE = 200;

    private final NotificationRepository notificationRepository;
    private final NotificationChannelFilter channelFilter;
    private final NotificationEmailSender emailSender;
    private final PushNotificationSender pushSender;
    private final ObjectMapper objectMapper;

    public NotificationService(
            NotificationRepository notificationRepository,
            NotificationChannelFilter channelFilter,
            NotificationEmailSender emailSender,
            PushNotificationSender pushSender,
            ObjectMapper objectMapper) {
        this.notificationRepository = notificationRepository;
        this.channelFilter = channelFilter;
        this.emailSender = emailSender;
        this.pushSender = pushSender;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public NotificationPageResponse list(UUID userId, boolean unreadOnly, int page, int size) {
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<NotificationEntity> result = unreadOnly
                ? notificationRepository.findUnreadByUserId(userId, pageable)
                : notificationRepository.findByUserIdOrderByCreatedAtDesc(userId, pageable);
        return toPage(result);
    }

    @Transactional(readOnly = true)
    public UnreadCountResponse unreadCount(UUID userId) {
        return new UnreadCountResponse(notificationRepository.countByUserIdAndReadAtIsNull(userId));
    }

    @Transactional
    public NotificationResponse markRead(UUID userId, UUID notificationId) {
        NotificationEntity entity = notificationRepository.findByIdAndUserId(notificationId, userId)
                .orElseThrow(() -> new NotFoundException("NOTIFICATION_NOT_FOUND", "Notification not found"));
        if (entity.getReadAt() == null) {
            entity.setReadAt(Instant.now());
            notificationRepository.save(entity);
        }
        return NotificationMapper.toResponse(entity, objectMapper);
    }

    @Transactional
    public int markAllRead(UUID userId) {
        return notificationRepository.markAllRead(userId, Instant.now());
    }

    @Transactional
    public void createThreadReplyBatch(
            List<UUID> recipientUserIds,
            UUID threadId,
            String threadTitle,
            UUID postId,
            String authorHandle) {
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }

        ThreadReplyNotificationPayload payload = new ThreadReplyNotificationPayload(
                threadId, threadTitle, postId, authorHandle);
        Map<String, List<UUID>> byChannel = channelFilter.enabledRecipientsByChannel(
                recipientUserIds,
                NotificationTypes.THREAD_REPLY);

        createWebNotifications(byChannel.getOrDefault(NotificationTypes.CHANNEL_WEB, List.of()), payload);
        emailSender.sendThreadReplyBatch(
                byChannel.getOrDefault(NotificationTypes.CHANNEL_EMAIL, List.of()),
                payload);
        pushSender.sendThreadReplyBatch(
                byChannel.getOrDefault(NotificationTypes.CHANNEL_PUSH, List.of()),
                payload);
    }

    private void createWebNotifications(List<UUID> recipientUserIds, ThreadReplyNotificationPayload payload) {
        if (recipientUserIds.isEmpty()) {
            return;
        }
        String dataJson = writeData(Map.of(
                "threadId", payload.threadId().toString(),
                "postId", payload.postId().toString(),
                "authorHandle", payload.authorHandle() != null ? payload.authorHandle() : ""));

        Instant now = Instant.now();
        List<NotificationEntity> batch = new ArrayList<>();
        for (UUID userId : recipientUserIds) {
            batch.add(NotificationEntity.create(
                    UUID.randomUUID(),
                    userId,
                    NotificationTypes.THREAD_REPLY,
                    payload.title(),
                    payload.body(),
                    dataJson,
                    now));
            if (batch.size() >= BATCH_CHUNK_SIZE) {
                notificationRepository.saveAll(batch);
                batch.clear();
            }
        }
        if (!batch.isEmpty()) {
            notificationRepository.saveAll(batch);
        }
    }

    private NotificationPageResponse toPage(Page<NotificationEntity> result) {
        return new NotificationPageResponse(
                result.getContent().stream()
                        .map(entity -> NotificationMapper.toResponse(entity, objectMapper))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private String writeData(Map<String, Object> data) {
        try {
            return objectMapper.writeValueAsString(data);
        } catch (Exception ex) {
            return "{}";
        }
    }
}
