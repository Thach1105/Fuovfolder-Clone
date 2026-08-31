package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.domain.IngestResource;
import com.fuoverflow.exam.support.Sha256;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.URI;
import java.net.UnknownHostException;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Locale;

/**
 * Downloads a PE resource archive named by a webhook payload.
 *
 * <p>Fetching a URL supplied by a caller is a server-side request forgery primitive, so the checks
 * here are the security boundary, not conveniences: only allowlisted hosts, only HTTPS, no
 * redirects (an allowlisted host must not be able to bounce us at an internal address), no private
 * or loopback targets, a hard size ceiling, and a content hash the sender committed to up front.
 */
@Component
public class ExamResourceFetcher {
    private static final Logger log = LoggerFactory.getLogger(ExamResourceFetcher.class);

    private final ExamWebhookProperties properties;
    private final boolean allowPlainHttp;
    private final HttpClient httpClient;

    public ExamResourceFetcher(ExamWebhookProperties properties) {
        this(properties, false);
    }

    ExamResourceFetcher(ExamWebhookProperties properties, boolean allowPlainHttp) {
        this.properties = properties;
        this.allowPlainHttp = allowPlainHttp;
        this.httpClient = HttpClient.newBuilder()
                .followRedirects(HttpClient.Redirect.NEVER)
                .connectTimeout(Duration.ofSeconds(10))
                .build();
    }

    public byte[] fetch(IngestResource resource) {
        URI uri = parse(resource.sourceUrl());
        verifyScheme(uri);
        verifyHost(uri);

        long maxBytes = properties.maxResourceBytesOrDefault();
        HttpResponse<byte[]> response;
        try {
            response = httpClient.send(
                    HttpRequest.newBuilder(uri)
                            .timeout(Duration.ofSeconds(60))
                            .GET()
                            .build(),
                    HttpResponse.BodyHandlers.ofByteArray());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw fetchFailed(resource, "bị ngắt khi tải");
        } catch (Exception e) {
            throw fetchFailed(resource, e.getClass().getSimpleName());
        }

        if (response.statusCode() != 200) {
            throw fetchFailed(resource, "HTTP " + response.statusCode());
        }

        long declaredLength = response.headers().firstValueAsLong("content-length").orElse(-1L);
        if (declaredLength > maxBytes) {
            throw tooLarge(resource, declaredLength, maxBytes);
        }
        byte[] body = response.body();
        if (body.length > maxBytes) {
            throw tooLarge(resource, body.length, maxBytes);
        }

        String actual = Sha256.hex(body);
        if (!actual.equalsIgnoreCase(resource.sha256())) {
            throw new BadRequestException("WEBHOOK_RESOURCE_HASH_MISMATCH",
                    "File " + resource.filename() + " có sha256 không khớp khai báo.");
        }
        return body;
    }

    private static URI parse(String sourceUrl) {
        try {
            URI uri = URI.create(sourceUrl);
            if (uri.getHost() == null) {
                throw new IllegalArgumentException("no host");
            }
            return uri;
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("WEBHOOK_RESOURCE_FETCH_FAILED",
                    "sourceUrl không phải URL hợp lệ.");
        }
    }

    private void verifyScheme(URI uri) {
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        boolean ok = "https".equals(scheme) || (allowPlainHttp && "http".equals(scheme));
        if (!ok) {
            throw new BadRequestException("WEBHOOK_RESOURCE_SCHEME_INVALID",
                    "sourceUrl phải dùng https.");
        }
    }

    private void verifyHost(URI uri) {
        String host = uri.getHost().toLowerCase(Locale.ROOT);
        boolean allowed = properties.allowedResourceHostsOrEmpty().stream()
                .anyMatch(candidate -> candidate != null && candidate.trim().equalsIgnoreCase(host));
        if (!allowed) {
            throw new BadRequestException("WEBHOOK_RESOURCE_HOST_NOT_ALLOWED",
                    "Host " + host + " không nằm trong allowlist.");
        }
        if (allowPlainHttp) {
            // Test/dev mode talks to a loopback server on purpose.
            return;
        }
        try {
            InetAddress address = InetAddress.getByName(host);
            if (address.isLoopbackAddress() || address.isLinkLocalAddress()
                    || address.isSiteLocalAddress() || address.isAnyLocalAddress()) {
                throw new BadRequestException("WEBHOOK_RESOURCE_HOST_NOT_ALLOWED",
                        "Host " + host + " trỏ tới địa chỉ nội bộ.");
            }
        } catch (UnknownHostException e) {
            throw new BadRequestException("WEBHOOK_RESOURCE_FETCH_FAILED",
                    "Không phân giải được host " + host + ".");
        }
    }

    private static BadRequestException fetchFailed(IngestResource resource, String reason) {
        log.warn("Failed to fetch exam resource {}: {}", resource.filename(), reason);
        return new BadRequestException("WEBHOOK_RESOURCE_FETCH_FAILED",
                "Không tải được file " + resource.filename() + " (" + reason + ").");
    }

    private static BadRequestException tooLarge(IngestResource resource, long size, long maxBytes) {
        return new BadRequestException("WEBHOOK_RESOURCE_TOO_LARGE",
                "File " + resource.filename() + " " + size + " byte vượt giới hạn " + maxBytes + ".");
    }
}
