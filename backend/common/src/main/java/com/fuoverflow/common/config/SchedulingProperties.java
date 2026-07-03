package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuexam.scheduling")
public record SchedulingProperties(
        int poolSize,
        String timezone
) {
    public SchedulingProperties {
        if (poolSize <= 0) {
            poolSize = 4;
        }
        if (timezone == null || timezone.isBlank()) {
            timezone = "Asia/Ho_Chi_Minh";
        }
    }
}
