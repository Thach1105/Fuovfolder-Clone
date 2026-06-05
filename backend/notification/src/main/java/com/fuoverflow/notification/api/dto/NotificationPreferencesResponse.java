package com.fuoverflow.notification.api.dto;

import java.util.List;

public record NotificationPreferencesResponse(List<NotificationPreferenceItemResponse> preferences) {
}
