package com.fuoverflow.notification.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.notification.config.NotificationProperties;
import com.fuoverflow.notification.domain.ThreadReplyNotificationPayload;
import com.fuoverflow.notification.persistence.PushSubscriptionEntity;
import com.fuoverflow.notification.persistence.PushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import nl.martijndwars.webpush.Subscription;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.security.GeneralSecurityException;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PushNotificationSender {
    private static final Logger log = LoggerFactory.getLogger(PushNotificationSender.class);

    private final PushSubscriptionRepository subscriptionRepository;
    private final NotificationProperties properties;
    private final ObjectMapper objectMapper;

    public PushNotificationSender(
            PushSubscriptionRepository subscriptionRepository,
            NotificationProperties properties,
            ObjectMapper objectMapper) {
        this.subscriptionRepository = subscriptionRepository;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Async
    public void sendThreadReplyBatch(List<UUID> recipientUserIds, ThreadReplyNotificationPayload payload) {
        NotificationProperties.Push config = properties.push();
        if (config == null || !config.enabled()
                || !StringUtils.hasText(config.vapidPublicKey())
                || !StringUtils.hasText(config.vapidPrivateKey())) {
            return;
        }
        if (recipientUserIds == null || recipientUserIds.isEmpty()) {
            return;
        }

        PushService pushService;
        try {
            pushService = new PushService(config.vapidPublicKey(), config.vapidPrivateKey());
            if (StringUtils.hasText(config.subject())) {
                pushService.setSubject(config.subject());
            }
        } catch (GeneralSecurityException ex) {
            log.warn("Push service unavailable: {}", ex.getMessage());
            return;
        }

        List<PushSubscriptionEntity> subscriptions = subscriptionRepository.findByUserIdIn(recipientUserIds);
        String threadUrl = threadUrl(payload.threadId());
        String jsonPayload = writePayload(payload, threadUrl);

        for (PushSubscriptionEntity subscription : subscriptions) {
            try {
                Notification notification = new Notification(
                        toSubscription(subscription),
                        jsonPayload);
                pushService.send(notification);
            } catch (Exception ex) {
                log.warn("Push failed for endpoint {}: {}", subscription.getEndpoint(), ex.getMessage());
                if (isExpired(ex)) {
                    subscriptionRepository.deleteByEndpoint(subscription.getEndpoint());
                }
            }
        }
    }

    private Subscription toSubscription(PushSubscriptionEntity entity) {
        return new Subscription(entity.getEndpoint(), new Subscription.Keys(entity.getP256dh(), entity.getAuthKey()));
    }

    private String writePayload(ThreadReplyNotificationPayload payload, String threadUrl) {
        try {
            return objectMapper.writeValueAsString(Map.of(
                    "title", payload.title(),
                    "body", payload.body(),
                    "url", threadUrl,
                    "threadId", payload.threadId().toString()));
        } catch (Exception ex) {
            return "{\"title\":\"FuOverflow\",\"body\":\"Có thông báo mới\"}";
        }
    }

    private String threadUrl(UUID threadId) {
        NotificationProperties.Email email = properties.email();
        String base = email != null ? email.threadUrlBase() : null;
        if (!StringUtils.hasText(base)) {
            return "/threads/" + threadId;
        }
        return base.endsWith("/") ? base + threadId : base + "/" + threadId;
    }

    private static boolean isExpired(Exception ex) {
        String message = ex.getMessage();
        return message != null && (message.contains("410") || message.contains("404"));
    }
}
