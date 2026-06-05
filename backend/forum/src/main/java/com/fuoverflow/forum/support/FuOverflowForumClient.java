package com.fuoverflow.forum.support;

import com.fuoverflow.forum.config.ForumCrawlerProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Thin HTTP client for fetching forum/thread HTML from the source site.
 *
 * <p>Built on the JDK {@link HttpClient} (no extra HTTP dependency). Applies a polite
 * fixed delay between requests, retries transient failures with linear backoff, and
 * optionally attaches an authenticated session cookie for the authenticated mode.
 */
@Component
public class FuOverflowForumClient {
    private static final Logger log = LoggerFactory.getLogger(FuOverflowForumClient.class);

    private final ForumCrawlerProperties properties;
    private final HttpClient httpClient;

    public FuOverflowForumClient(ForumCrawlerProperties properties) {
        this.properties = properties;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NORMAL)
                .connectTimeout(Duration.ofMillis(properties.timeoutMsOrDefault()))
                .build();
    }

    public String fetchForumPage(String forumSlug, int page, String cookie) {
        String path = "/forums/" + forumSlug + "/";
        if (page > 1) {
            path += "page-" + page;
        }
        return get(absolute(path), cookie);
    }

    public String fetchThreadPage(String threadPath, int page, String cookie) {
        String path = normalizeThreadPath(threadPath);
        if (page > 1) {
            path += "page-" + page;
        }
        return get(absolute(path), cookie);
    }

    public String get(String url, String cookie) {
        int attempts = properties.maxRetriesOrDefault() + 1;
        RuntimeException last = null;
        for (int attempt = 1; attempt <= attempts; attempt++) {
            applyDelay();
            try {
                HttpRequest.Builder builder = HttpRequest.newBuilder()
                        .uri(URI.create(url))
                        .timeout(Duration.ofMillis(properties.timeoutMsOrDefault()))
                        .header("User-Agent", properties.userAgentOrDefault())
                        .header("Accept", "text/html,application/xhtml+xml")
                        .header("Accept-Language", "vi,en;q=0.8")
                        .GET();
                String effectiveCookie = cookie != null && !cookie.isBlank()
                        ? cookie
                        : properties.authCookie();
                if (effectiveCookie != null && !effectiveCookie.isBlank()) {
                    builder.header("Cookie", effectiveCookie);
                }
                HttpResponse<String> response =
                        httpClient.send(builder.build(), HttpResponse.BodyHandlers.ofString());
                int status = response.statusCode();
                if (status == 200) {
                    return response.body();
                }
                last = new ForumCrawlException("Unexpected status " + status + " for " + url);
                log.warn("Crawl attempt {} for {} returned status {}", attempt, url, status);
            } catch (java.io.IOException ex) {
                last = new ForumCrawlException("IO error fetching " + url, ex);
                log.warn("Crawl attempt {} for {} failed: {}", attempt, url, ex.getMessage());
            } catch (InterruptedException ex) {
                Thread.currentThread().interrupt();
                throw new ForumCrawlException("Interrupted fetching " + url, ex);
            }
            backoff(attempt);
        }
        throw last != null ? last : new ForumCrawlException("Failed to fetch " + url);
    }

    private String absolute(String path) {
        return properties.baseUrlOrDefault() + path;
    }

    private static String normalizeThreadPath(String threadPath) {
        String path = threadPath.startsWith("http")
                ? URI.create(threadPath).getPath()
                : threadPath;
        if (!path.startsWith("/")) {
            path = "/" + path;
        }
        if (!path.endsWith("/")) {
            path += "/";
        }
        return path;
    }

    private void applyDelay() {
        int delay = properties.requestDelayMsOrDefault();
        if (delay > 0) {
            sleep(delay);
        }
    }

    private void backoff(int attempt) {
        sleep(Math.min(5000L, 500L * attempt));
    }

    private static void sleep(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ex) {
            Thread.currentThread().interrupt();
        }
    }
}
