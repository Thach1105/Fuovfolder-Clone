package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "app.support")
public record SupportProperties(String email, String phone) {
    public SupportProperties {
        email = email != null ? email : "";
        phone = phone != null ? phone : "";
    }
}
