package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.domain.IngestResource;
import com.fuoverflow.exam.support.Sha256;
import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamResourceFetcherTest {

    private static final byte[] ZIP = "PKpayload-bytes".getBytes();

    private HttpServer server;
    private String baseUrl;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/a.zip", exchange -> {
            exchange.sendResponseHeaders(200, ZIP.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(ZIP);
            }
        });
        server.createContext("/missing.zip", exchange -> {
            exchange.sendResponseHeaders(404, -1);
            exchange.close();
        });
        server.createContext("/redirect.zip", exchange -> {
            exchange.getResponseHeaders().add("Location", "http://127.0.0.1/elsewhere.zip");
            exchange.sendResponseHeaders(302, -1);
            exchange.close();
        });
        server.start();
        baseUrl = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    @Test
    void downloadsFromAnAllowlistedHostAndVerifiesTheHash() {
        byte[] bytes = fetcher(List.of("127.0.0.1"), null, true)
                .fetch(resource(baseUrl + "/a.zip", Sha256.hex(ZIP)));

        assertArrayEquals(ZIP, bytes);
    }

    @Test
    void rejectsAHostOutsideTheAllowlist() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("cdn.example.com"), null, true)
                        .fetch(resource(baseUrl + "/a.zip", Sha256.hex(ZIP))));

        assertEquals("WEBHOOK_RESOURCE_HOST_NOT_ALLOWED", ex.code());
    }

    @Test
    void rejectsANonHttpsUrlWhenPlainHttpIsNotAllowed() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), null, false)
                        .fetch(resource(baseUrl + "/a.zip", Sha256.hex(ZIP))));

        assertEquals("WEBHOOK_RESOURCE_SCHEME_INVALID", ex.code());
    }

    @Test
    void rejectsAHashMismatch() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), null, true)
                        .fetch(resource(baseUrl + "/a.zip", "c".repeat(64))));

        assertEquals("WEBHOOK_RESOURCE_HASH_MISMATCH", ex.code());
    }

    @Test
    void rejectsABodyOverTheSizeCeiling() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), 2L, true)
                        .fetch(resource(baseUrl + "/a.zip", Sha256.hex(ZIP))));

        assertEquals("WEBHOOK_RESOURCE_TOO_LARGE", ex.code());
    }

    @Test
    void reportsANonSuccessStatusAsAFetchFailure() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), null, true)
                        .fetch(resource(baseUrl + "/missing.zip", Sha256.hex(ZIP))));

        assertEquals("WEBHOOK_RESOURCE_FETCH_FAILED", ex.code());
    }

    @Test
    void refusesToFollowARedirect() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), null, true)
                        .fetch(resource(baseUrl + "/redirect.zip", Sha256.hex(ZIP))));

        assertEquals("WEBHOOK_RESOURCE_FETCH_FAILED", ex.code());
    }

    @Test
    void rejectsAMalformedUrl() {
        BadRequestException ex = assertThrows(BadRequestException.class, () ->
                fetcher(List.of("127.0.0.1"), null, true)
                        .fetch(resource("not a url", Sha256.hex(ZIP))));

        assertEquals("WEBHOOK_RESOURCE_FETCH_FAILED", ex.code());
    }

    private ExamResourceFetcher fetcher(List<String> hosts, Long maxBytes, boolean allowPlainHttp) {
        ExamWebhookProperties properties = new ExamWebhookProperties(
                Map.of(), hosts, null, null, maxBytes, null, null, null, null);
        return new ExamResourceFetcher(properties, allowPlainHttp);
    }

    private static IngestResource resource(String url, String sha256) {
        return new IngestResource(0, null, "a.zip", "application/zip", ZIP.length, sha256, url);
    }
}
