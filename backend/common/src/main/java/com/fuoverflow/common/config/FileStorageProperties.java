package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuoverflow.storage")
public record FileStorageProperties(
        String uploadsPath,
        String logsPath,
        String tempPath
) {
}
