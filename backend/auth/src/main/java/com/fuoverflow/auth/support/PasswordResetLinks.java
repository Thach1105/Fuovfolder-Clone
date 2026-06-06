package com.fuoverflow.auth.support;

import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class PasswordResetLinks {
    private static final String API_RESET_PATH = "/api/v1/auth/password/reset";
    private static final String FRONTEND_RESET_PATH = "/reset-password";

    private PasswordResetLinks() {
    }

    public static String resolvePageBase(String resetUrlBase, List<String> corsAllowedOrigins) {
        if (!StringUtils.hasText(resetUrlBase)) {
            throw new IllegalStateException(
                    "Password reset URL is not configured. Set AUTH_PASSWORD_RESET_URL_BASE to the frontend reset page.");
        }
        String trimmed = resetUrlBase.trim();
        if (trimmed.contains(API_RESET_PATH)) {
            if (corsAllowedOrigins != null && !corsAllowedOrigins.isEmpty()) {
                String origin = corsAllowedOrigins.get(0).trim();
                return trimTrailingSlash(origin) + FRONTEND_RESET_PATH;
            }
            throw new IllegalStateException(
                    "AUTH_PASSWORD_RESET_URL_BASE must be the frontend reset page (…/reset-password), not the backend API.");
        }
        return trimmed;
    }

    public static String buildLink(String resetUrlBase, List<String> corsAllowedOrigins, String token) {
        String pageBase = resolvePageBase(resetUrlBase, corsAllowedOrigins);
        String separator = pageBase.contains("?") ? "&" : "?";
        return pageBase + separator + "token=" + URLEncoder.encode(token, StandardCharsets.UTF_8);
    }

    private static String trimTrailingSlash(String value) {
        while (value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }
}
