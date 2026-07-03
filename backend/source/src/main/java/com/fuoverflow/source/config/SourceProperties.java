package com.fuoverflow.source.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuexam.source")
public record SourceProperties(
        Boolean refundEnabled,
        Integer defaultPageSize,
        String mediaSigningSecret,
        Integer signedUrlTtlSeconds
) {
    public boolean refundEnabledOrDefault() {
        return refundEnabled == null || refundEnabled;
    }

    public int defaultPageSizeOrDefault() {
        return defaultPageSize != null && defaultPageSize > 0 ? defaultPageSize : 24;
    }

    public String mediaSigningSecretOrDefault() {
        return mediaSigningSecret != null && !mediaSigningSecret.isBlank()
                ? mediaSigningSecret : "dev-source-media-secret-change-me";
    }

    public int signedUrlTtlSecondsOrDefault() {
        return signedUrlTtlSeconds != null && signedUrlTtlSeconds > 0
                ? signedUrlTtlSeconds : 900;
    }
}
