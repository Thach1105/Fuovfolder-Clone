package com.fuoverflow.notification.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuoverflow.notification")
public record NotificationProperties(
        Email email,
        Push push
) {
    public record Email(
            boolean enabled,
            String from,
            String subject,
            String threadUrlBase
    ) {
    }

    public record Push(
            boolean enabled,
            String vapidPublicKey,
            String vapidPrivateKey,
            String subject
    ) {
    }
}
