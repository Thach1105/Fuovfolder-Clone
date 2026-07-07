package com.fuoverflow.exam.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.List;

/**
 * Serialization helpers for the jsonb image-url arrays stored on FE questions and PE items.
 */
final class ExamJsonUtil {
    private static final Logger log = LoggerFactory.getLogger(ExamJsonUtil.class);

    private ExamJsonUtil() {
    }

    static String serialize(ObjectMapper objectMapper, List<String> urls) {
        if (urls == null || urls.isEmpty()) {
            return "[]";
        }
        try {
            return objectMapper.writeValueAsString(urls);
        } catch (JsonProcessingException e) {
            log.error("Failed to serialize image URLs", e);
            return "[]";
        }
    }

    static List<String> deserialize(ObjectMapper objectMapper, String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            log.error("Failed to deserialize image URLs", e);
            return List.of();
        }
    }
}
