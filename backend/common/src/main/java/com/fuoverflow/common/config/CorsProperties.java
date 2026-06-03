package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

@ConfigurationProperties(prefix = "fuoverflow.cors")
public record CorsProperties(
        List<String> allowedOrigins,
        boolean allowCredentials
) {
}
