package com.fuoverflow.auth.support;

import org.springframework.util.StringUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class EmailVerificationLinks {
    private static final String API_VERIFY_PATH = "/api/v1/auth/email/verify";
    private static final String FRONTEND_VERIFY_PATH = "/verify-email";

    private EmailVerificationLinks() {
    }

    public static String resolvePageBase(String verificationUrlBase, List<String> corsAllowedOrigins) {
        if (!StringUtils.hasText(verificationUrlBase)) {
            throw new IllegalStateException(
                    "Email verification URL is not configured. Set AUTH_EMAIL_VERIFICATION_URL_BASE to the frontend verify page.");
        }
        String trimmed = verificationUrlBase.trim();
        if (trimmed.contains(API_VERIFY_PATH)) {
            if (corsAllowedOrigins != null && !corsAllowedOrigins.isEmpty()) {
                String origin = corsAllowedOrigins.get(0).trim();
                return trimTrailingSlash(origin) + FRONTEND_VERIFY_PATH;
            }
            throw new IllegalStateException(
                    "AUTH_EMAIL_VERIFICATION_URL_BASE must be the frontend verify page (…/verify-email), not the backend API.");
        }
        return trimmed;
    }

    public static String buildLink(String verificationUrlBase, List<String> corsAllowedOrigins, String token) {
        String pageBase = resolvePageBase(verificationUrlBase, corsAllowedOrigins);
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
