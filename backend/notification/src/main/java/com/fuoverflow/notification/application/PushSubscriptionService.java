package com.fuoverflow.notification.application;

import com.fuoverflow.notification.api.dto.PushSubscribeRequest;
import com.fuoverflow.notification.config.NotificationProperties;
import com.fuoverflow.notification.persistence.PushSubscriptionEntity;
import com.fuoverflow.notification.persistence.PushSubscriptionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

@Service
public class PushSubscriptionService {
    private final PushSubscriptionRepository repository;
    private final NotificationProperties properties;

    public PushSubscriptionService(PushSubscriptionRepository repository, NotificationProperties properties) {
        this.repository = repository;
        this.properties = properties;
    }

    @Transactional
    public Map<String, Boolean> subscribe(UUID userId, PushSubscribeRequest request, String userAgent) {
        Instant now = Instant.now();
        PushSubscriptionEntity existing = repository.findByEndpoint(request.endpoint()).orElse(null);
        if (existing == null) {
            repository.save(PushSubscriptionEntity.create(
                    UUID.randomUUID(),
                    userId,
                    request.endpoint().trim(),
                    request.p256dh().trim(),
                    request.auth().trim(),
                    userAgent,
                    now));
        } else {
            existing.refresh(request.p256dh().trim(), request.auth().trim(), userAgent, now);
        }
        return Map.of("subscribed", true);
    }

    @Transactional
    public void unsubscribe(String endpoint) {
        if (endpoint != null && !endpoint.isBlank()) {
            repository.deleteByEndpoint(endpoint.trim());
        }
    }

    @Transactional(readOnly = true)
    public long countForUser(UUID userId) {
        return repository.countByUserId(userId);
    }

    @Transactional(readOnly = true)
    public boolean pushEnabled() {
        NotificationProperties.Push push = properties.push();
        return push != null && push.enabled()
                && push.vapidPublicKey() != null && !push.vapidPublicKey().isBlank();
    }

    @Transactional(readOnly = true)
    public String publicKey() {
        NotificationProperties.Push push = properties.push();
        return push == null ? "" : push.vapidPublicKey();
    }
}
