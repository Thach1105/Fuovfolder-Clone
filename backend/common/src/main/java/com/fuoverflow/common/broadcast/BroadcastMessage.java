package com.fuoverflow.common.broadcast;

import java.time.Instant;
import java.util.Map;

public record BroadcastMessage(
        String eventType,
        String message,
        Map<String, Object> data,
        Instant timestamp
) {
    public BroadcastMessage(String eventType, String message, Map<String, Object> data) {
        this(eventType, message, data, Instant.now());
    }
}
