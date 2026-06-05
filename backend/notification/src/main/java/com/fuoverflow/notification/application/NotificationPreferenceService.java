package com.fuoverflow.notification.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.notification.api.dto.NotificationPreferenceItemResponse;
import com.fuoverflow.notification.api.dto.NotificationPreferenceUpdateRequest;
import com.fuoverflow.notification.api.dto.NotificationPreferencesResponse;
import com.fuoverflow.notification.domain.NotificationTypes;
import com.fuoverflow.notification.persistence.NotificationPreferenceEntity;
import com.fuoverflow.notification.persistence.NotificationPreferenceRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class NotificationPreferenceService {
    private static final List<String> CHANNELS = List.of(
            NotificationTypes.CHANNEL_WEB,
            NotificationTypes.CHANNEL_EMAIL,
            NotificationTypes.CHANNEL_PUSH);
    private static final List<String> TYPES = List.of(NotificationTypes.THREAD_REPLY);

    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationPreferenceService(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    @Transactional(readOnly = true)
    public NotificationPreferencesResponse getForUser(UUID userId) {
        Map<String, NotificationPreferenceEntity> existing = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        pref -> key(pref.getType(), pref.getChannel()),
                        Function.identity(),
                        (left, right) -> left));

        List<NotificationPreferenceItemResponse> items = new ArrayList<>();
        for (String type : TYPES) {
            for (String channel : CHANNELS) {
                NotificationPreferenceEntity pref = existing.get(key(type, channel));
                items.add(new NotificationPreferenceItemResponse(
                        type,
                        channel,
                        pref == null || pref.isEnabled()));
            }
        }
        return new NotificationPreferencesResponse(items);
    }

    @Transactional
    public NotificationPreferencesResponse update(UUID userId, NotificationPreferenceUpdateRequest request) {
        Instant now = Instant.now();
        Map<String, NotificationPreferenceEntity> existing = preferenceRepository.findByUserId(userId).stream()
                .collect(Collectors.toMap(
                        pref -> key(pref.getType(), pref.getChannel()),
                        Function.identity(),
                        (left, right) -> left));

        for (NotificationPreferenceUpdateRequest.Item item : request.preferences()) {
            validateTypeAndChannel(item.type(), item.channel());
            String lookupKey = key(item.type(), item.channel());
            NotificationPreferenceEntity pref = existing.get(lookupKey);
            if (pref == null) {
                preferenceRepository.save(NotificationPreferenceEntity.create(
                        UUID.randomUUID(), userId, item.channel(), item.type(), item.enabled(), now));
            } else {
                pref.setEnabled(item.enabled());
                pref.setUpdatedAt(now);
            }
        }
        return getForUser(userId);
    }

    private static void validateTypeAndChannel(String type, String channel) {
        if (!TYPES.contains(type) || !CHANNELS.contains(channel)) {
            throw new BadRequestException("NOTIFICATION_PREF_INVALID", "Unsupported notification preference");
        }
    }

    private static String key(String type, String channel) {
        return type + ":" + channel;
    }
}
