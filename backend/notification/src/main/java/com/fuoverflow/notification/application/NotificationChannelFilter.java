package com.fuoverflow.notification.application;

import com.fuoverflow.notification.domain.NotificationTypes;
import com.fuoverflow.notification.persistence.NotificationPreferenceEntity;
import com.fuoverflow.notification.persistence.NotificationPreferenceRepository;
import org.springframework.stereotype.Component;

import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Component
public class NotificationChannelFilter {
    private final NotificationPreferenceRepository preferenceRepository;

    public NotificationChannelFilter(NotificationPreferenceRepository preferenceRepository) {
        this.preferenceRepository = preferenceRepository;
    }

    public Map<String, List<UUID>> enabledRecipientsByChannel(Collection<UUID> userIds, String notificationType) {
        if (userIds == null || userIds.isEmpty()) {
            return Map.of();
        }
        List<UUID> all = List.copyOf(userIds);
        Set<UUID> disabledWeb = disabledForChannel(all, notificationType, NotificationTypes.CHANNEL_WEB);
        Set<UUID> disabledEmail = disabledForChannel(all, notificationType, NotificationTypes.CHANNEL_EMAIL);
        Set<UUID> disabledPush = disabledForChannel(all, notificationType, NotificationTypes.CHANNEL_PUSH);

        Map<String, List<UUID>> result = new HashMap<>();
        result.put(NotificationTypes.CHANNEL_WEB, filter(all, disabledWeb));
        result.put(NotificationTypes.CHANNEL_EMAIL, filter(all, disabledEmail));
        result.put(NotificationTypes.CHANNEL_PUSH, filter(all, disabledPush));
        return result;
    }

    private Set<UUID> disabledForChannel(List<UUID> userIds, String type, String channel) {
        return new HashSet<>(preferenceRepository.findDisabledAmong(userIds, channel, type).stream()
                .map(NotificationPreferenceEntity::getUserId)
                .toList());
    }

    private static List<UUID> filter(List<UUID> userIds, Set<UUID> disabled) {
        return userIds.stream().filter(id -> !disabled.contains(id)).toList();
    }
}
