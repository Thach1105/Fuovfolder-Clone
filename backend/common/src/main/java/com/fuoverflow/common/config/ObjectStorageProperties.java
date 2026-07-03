package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuexam.storage")
public record ObjectStorageProperties(
        String provider,
        String uploadsPath,
        S3 s3
) {
    public record S3(
            String endpoint,
            String region,
            String bucket,
            String accessKey,
            String secretKey,
            String publicBaseUrl,
            Boolean pathStyleAccess
    ) {
        public boolean pathStyleAccessOrDefault() {
            return pathStyleAccess == null || pathStyleAccess;
        }
    }

    public boolean useS3() {
        return provider == null || "s3".equalsIgnoreCase(provider);
    }

    public boolean useLocal() {
        return "local".equalsIgnoreCase(provider);
    }
}
