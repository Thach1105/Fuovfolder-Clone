package com.fuoverflow.exam.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuexam.exam")
public record ExamProperties(
        String mediaSigningSecret,
        Integer signedUrlTtlSeconds,
        Integer defaultFePreviewImageCount
) {
    public String mediaSigningSecretOrDefault() {
        return mediaSigningSecret != null && !mediaSigningSecret.isBlank()
                ? mediaSigningSecret : "dev-exam-media-secret-change-me";
    }

    public int signedUrlTtlSecondsOrDefault() {
        return signedUrlTtlSeconds != null && signedUrlTtlSeconds > 0
                ? signedUrlTtlSeconds : 900;
    }

    public int defaultFePreviewImageCountOrDefault() {
        return defaultFePreviewImageCount != null && defaultFePreviewImageCount >= 0
                ? defaultFePreviewImageCount : 3;
    }
}
