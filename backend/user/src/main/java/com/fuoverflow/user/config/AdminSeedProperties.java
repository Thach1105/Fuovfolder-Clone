package com.fuoverflow.user.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuoverflow.admin.seed")
public record AdminSeedProperties(
        boolean enabled,
        String email,
        String username,
        String password,
        String displayName
) {
}
