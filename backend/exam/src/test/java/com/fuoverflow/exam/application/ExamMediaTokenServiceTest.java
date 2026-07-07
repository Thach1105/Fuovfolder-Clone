package com.fuoverflow.exam.application;

import com.fuoverflow.exam.config.ExamProperties;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamMediaTokenServiceTest {
    private ExamMediaTokenService tokenService;

    @BeforeEach
    void setUp() {
        tokenService = new ExamMediaTokenService(new ExamProperties("test-secret", 900, 3));
    }

    @Test
    void generateAndVerifySignedUrl() {
        String key = "exam/fe/2026/07/07/abc.png";
        String url = tokenService.generateSignedUrl(key);
        assertTrue(url.startsWith("/api/v1/exam/media/"));
        assertTrue(url.contains("sig="));
        assertTrue(url.contains("exp="));

        long exp = Long.parseLong(url.substring(url.indexOf("exp=") + 4));
        String sig = url.substring(url.indexOf("sig=") + 4, url.indexOf("&exp="));
        assertTrue(tokenService.verify(key, sig, exp));
    }

    @Test
    void verifyRejectsTamperedSignature() {
        String key = "exam/fe/x.png";
        long exp = Instant.now().plusSeconds(900).getEpochSecond();
        assertFalse(tokenService.verify(key, "deadbeef", exp));
    }

    @Test
    void verifyRejectsExpired() {
        String key = "exam/fe/x.png";
        long past = Instant.now().minusSeconds(10).getEpochSecond();
        String url = tokenService.generateSignedUrl(key);
        String sig = url.substring(url.indexOf("sig=") + 4, url.indexOf("&exp="));
        assertFalse(tokenService.verify(key, sig, past));
        assertTrue(tokenService.isExpired(past));
    }

    @Test
    void encodeDecodeRoundTrip() {
        String key = "exam/pe/2026/07/07/deck.png";
        assertEquals(key, tokenService.decodeKey(tokenService.encodeKey(key)));
    }

    @Test
    void nullKeyReturnsNull() {
        assertNull(tokenService.generateSignedUrl(null));
        assertNull(tokenService.generateSignedUrl("  "));
    }
}
