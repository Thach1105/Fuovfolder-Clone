package com.fuoverflow.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

@ConfigurationProperties(prefix = "fuexam.rate-limit")
public record RateLimitProperties(
        boolean enabled,
        TierLimits auth,
        TierLimits publicEndpoints,
        TierLimits api,
        TierLimits global,
        BlockConfig block
) {
    public record TierLimits(
            int anonymousMax,
            int authenticatedMax,
            Duration window
    ) {}

    public record BlockConfig(
            Duration autoBlockDuration,
            int violationsBeforeBlock
    ) {}
}
