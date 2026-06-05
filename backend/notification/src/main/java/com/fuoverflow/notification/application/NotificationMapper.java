package com.fuoverflow.notification.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.notification.api.dto.NotificationResponse;
import com.fuoverflow.notification.persistence.NotificationEntity;

import java.util.Map;

public final class NotificationMapper {
    private static final TypeReference<Map<String, Object>> DATA_TYPE = new TypeReference<>() {
    };

    private NotificationMapper() {
    }

    public static NotificationResponse toResponse(NotificationEntity entity, ObjectMapper objectMapper) {
        return new NotificationResponse(
                entity.getId(),
                entity.getType(),
                entity.getTitle(),
                entity.getBody(),
                parseData(entity.getDataJson(), objectMapper),
                entity.getReadAt() != null,
                entity.getCreatedAt());
    }

    private static Map<String, Object> parseData(String json, ObjectMapper objectMapper) {
        try {
            return objectMapper.readValue(json, DATA_TYPE);
        } catch (Exception ignored) {
            return Map.of();
        }
    }
}
