package com.fuoverflow.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuexam.exam")
public record ExamProperties(
        String mediaSigningSecret,
        Integer signedUrlTtlSeconds,
        Integer defaultFePreviewCount
) {
    public String mediaSigningSecretOrDefault() {
        return mediaSigningSecret != null && !mediaSigningSecret.isBlank()
                ? mediaSigningSecret : "dev-exam-media-secret-change-me";
    }

    public int signedUrlTtlSecondsOrDefault() {
        return signedUrlTtlSeconds != null && signedUrlTtlSeconds > 0
                ? signedUrlTtlSeconds : 900;
    }

    public int defaultFePreviewCountOrDefault() {
        return defaultFePreviewCount != null && defaultFePreviewCount >= 0
                ? defaultFePreviewCount : 3;
    }
}
