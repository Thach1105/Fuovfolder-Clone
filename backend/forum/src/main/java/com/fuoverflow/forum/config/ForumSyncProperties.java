package com.fuoverflow.forum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Scheduling configuration for recurring incremental crawls. Disabled by default; the
 * crawl can always be triggered on demand via the admin endpoint.
 */
@ConfigurationProperties(prefix = "fuoverflow.forum.sync")
public record ForumSyncProperties(
        Boolean enabled,
        Long intervalMs,
        String mode,
        String scope
) {
    public boolean enabledOrDefault() {
        return Boolean.TRUE.equals(enabled);
    }
}
