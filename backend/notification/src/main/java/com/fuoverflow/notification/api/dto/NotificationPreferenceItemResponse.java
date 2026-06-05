package com.fuoverflow.notification.api.dto;

public record NotificationPreferenceItemResponse(
        String type,
        String channel,
        boolean enabled
) {
}
