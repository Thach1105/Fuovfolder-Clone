package com.fuoverflow.coursera.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuoverflow.coursera")
public record CourseraProperties(
        CredentialsEncryption credentials,
        boolean refundOnCancel
) {
    public record CredentialsEncryption(
            String encryptionKey,
            String keyId
    ) {
    }
}
