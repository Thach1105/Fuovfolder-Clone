package com.fuoverflow.forum.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Crawl configuration for the fuoverflow.com forum source.
 *
 * <p>The {@code authCookie} is intentionally read from the environment only and must
 * never be committed; it is used for the authenticated crawl mode.
 */
@ConfigurationProperties(prefix = "fuoverflow.forum.crawler")
public record ForumCrawlerProperties(
        String baseUrl,
        String userAgent,
        Integer requestDelayMs,
        Integer timeoutMs,
        Integer maxRetries,
        String authCookie,
        List<String> forumSlugs,
        Integer maxThreadPagesPerForum,
        Integer maxPostPagesPerThread,
        Integer maxThreadsPerForum
) {
    public String baseUrlOrDefault() {
        return notBlank(baseUrl) ? stripTrailingSlash(baseUrl) : "https://fuoverflow.com";
    }

    public String userAgentOrDefault() {
        return notBlank(userAgent)
                ? userAgent
                : "Mozilla/5.0 (compatible; FuOverflowSyncBot/1.0; +https://fuoverflow.com)";
    }

    public int requestDelayMsOrDefault() {
        return requestDelayMs != null && requestDelayMs >= 0 ? requestDelayMs : 1500;
    }

    public int timeoutMsOrDefault() {
        return timeoutMs != null && timeoutMs > 0 ? timeoutMs : 20000;
    }

    public int maxRetriesOrDefault() {
        return maxRetries != null && maxRetries >= 0 ? maxRetries : 2;
    }

    public List<String> forumSlugsOrDefault() {
        return forumSlugs != null && !forumSlugs.isEmpty() ? forumSlugs : List.of("hoi-dap");
    }

    public int maxThreadPagesPerForumOrDefault() {
        return maxThreadPagesPerForum != null && maxThreadPagesPerForum > 0 ? maxThreadPagesPerForum : 1;
    }

    public int maxPostPagesPerThreadOrDefault() {
        return maxPostPagesPerThread != null && maxPostPagesPerThread > 0 ? maxPostPagesPerThread : 1;
    }

    public int maxThreadsPerForumOrDefault() {
        return maxThreadsPerForum != null && maxThreadsPerForum > 0 ? maxThreadsPerForum : 30;
    }

    private static boolean notBlank(String v) {
        return v != null && !v.isBlank();
    }

    private static String stripTrailingSlash(String v) {
        return v.endsWith("/") ? v.substring(0, v.length() - 1) : v;
    }
}
