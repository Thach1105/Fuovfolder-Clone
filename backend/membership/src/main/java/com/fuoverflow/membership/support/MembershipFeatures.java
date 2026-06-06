package com.fuoverflow.membership.support;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

public final class MembershipFeatures {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private MembershipFeatures() {
    }

    public static String buildFeaturesJson(String roleSlug, int durationDays) {
        ObjectNode node = MAPPER.createObjectNode();
        node.put("role_slug", roleSlug);
        node.put("duration_days", durationDays);
        return node.toString();
    }

    public static String roleSlug(String featuresJson) {
        try {
            JsonNode node = MAPPER.readTree(featuresJson == null ? "{}" : featuresJson);
            JsonNode role = node.get("role_slug");
            return role == null ? null : role.asText();
        } catch (Exception exception) {
            return null;
        }
    }

    public static int durationDays(String featuresJson, int fallback) {
        try {
            JsonNode node = MAPPER.readTree(featuresJson == null ? "{}" : featuresJson);
            JsonNode days = node.get("duration_days");
            return days == null ? fallback : days.asInt(fallback);
        } catch (Exception exception) {
            return fallback;
        }
    }
}
