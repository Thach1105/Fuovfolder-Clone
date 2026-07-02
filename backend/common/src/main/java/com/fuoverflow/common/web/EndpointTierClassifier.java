package com.fuoverflow.common.web;

import java.util.List;

public final class EndpointTierClassifier {

    private EndpointTierClassifier() {}

    public enum Tier { AUTH, PUBLIC, API, GLOBAL }

    private static final List<String> AUTH_PATHS = List.of(
            "/api/v1/auth/login",
            "/api/v1/auth/register",
            "/api/v1/auth/refresh",
            "/api/v1/auth/password/forgot",
            "/api/v1/auth/password/reset",
            "/api/v1/auth/password/set",
            "/api/v1/auth/email/resend"
    );

    private static final List<String> PUBLIC_PATH_PREFIXES = List.of(
            "/api/v1/forums",
            "/api/v1/threads",
            "/api/v1/coursera/catalog",
            "/api/v1/source/catalog",
            "/api/v1/membership/plans",
            "/api/v1/broadcasts"
    );

    public static Tier classify(String method, String uri) {
        if (AUTH_PATHS.stream().anyMatch(uri::equals)) return Tier.AUTH;
        if ("GET".equalsIgnoreCase(method)
                && PUBLIC_PATH_PREFIXES.stream().anyMatch(uri::startsWith)) {
            return Tier.PUBLIC;
        }
        if (!"GET".equalsIgnoreCase(method)) return Tier.API;
        return Tier.GLOBAL;
    }
}
