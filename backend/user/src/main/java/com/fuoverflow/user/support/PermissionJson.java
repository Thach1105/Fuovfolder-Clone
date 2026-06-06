package com.fuoverflow.user.support;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

public final class PermissionJson {
    private static final ObjectMapper MAPPER = new ObjectMapper();

    private PermissionJson() {
    }

    public static List<String> parseSlugs(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return MAPPER.readValue(json, new TypeReference<>() {
            });
        } catch (Exception exception) {
            return List.of();
        }
    }

    public static String toJson(List<String> slugs) {
        try {
            return MAPPER.writeValueAsString(slugs == null ? List.of() : slugs);
        } catch (Exception exception) {
            return "[]";
        }
    }

    public static List<String> mergeUnique(List<String> base, List<String> extra) {
        var merged = new ArrayList<>(base);
        for (String slug : extra) {
            if (!merged.contains(slug)) {
                merged.add(slug);
            }
        }
        return merged;
    }
}
