package com.fuoverflow.source.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "fuoverflow.source")
public record SourceProperties(
        Boolean refundEnabled,
        Integer defaultPageSize
) {
    public boolean refundEnabledOrDefault() {
        return refundEnabled == null || refundEnabled;
    }

    public int defaultPageSizeOrDefault() {
        return defaultPageSize != null && defaultPageSize > 0 ? defaultPageSize : 24;
    }
}
