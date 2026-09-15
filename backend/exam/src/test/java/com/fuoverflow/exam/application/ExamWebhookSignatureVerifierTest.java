package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamWebhookSignatureVerifierTest {

    private static final String SECRET = "s3cr3t-for-eos-crawler";
    private static final String BODY = "{\"eventId\":\"abc\"}";

    private final ExamWebhookSignatureVerifier verifier = new ExamWebhookSignatureVerifier(
            new ExamWebhookProperties(Map.of("eos-crawler", SECRET),
                    List.of("cdn.example.com"), null, null, null, null, null, null, null, null));

    @Test
    void acceptsAFreshValidSignature() {
        assertDoesNotThrow(() -> verifier.verify("eos-crawler", BODY, headerFor(BODY)));
    }

    @Test
    void acceptsAHeaderWithSpacesAndReversedOrder() {
        long now = Instant.now().getEpochSecond();
        String header = "v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, BODY) + ", t=" + now;

        assertDoesNotThrow(() -> verifier.verify("eos-crawler", BODY, header));
    }

    @Test
    void rejectsATamperedBody() {
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> verifier.verify("eos-crawler", BODY + " ", headerFor(BODY)));

        assertEquals("WEBHOOK_SIGNATURE_INVALID", ex.code());
    }

    @Test
    void rejectsAnExpiredTimestamp() {
        long stale = Instant.now().getEpochSecond() - 301;
        String header = "t=" + stale + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, stale, BODY);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> verifier.verify("eos-crawler", BODY, header));

        assertEquals("WEBHOOK_SIGNATURE_EXPIRED", ex.code());
    }

    @Test
    void rejectsATimestampTooFarInTheFuture() {
        long ahead = Instant.now().getEpochSecond() + 400;
        String header = "t=" + ahead + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, ahead, BODY);

        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> verifier.verify("eos-crawler", BODY, header));

        assertEquals("WEBHOOK_SIGNATURE_EXPIRED", ex.code());
    }

    @Test
    void rejectsAnUnknownClient() {
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> verifier.verify("someone-else", BODY, headerFor(BODY)));

        assertEquals("WEBHOOK_CLIENT_UNKNOWN", ex.code());
    }

    @Test
    void rejectsAMissingClientId() {
        UnauthorizedException ex = assertThrows(UnauthorizedException.class,
                () -> verifier.verify(null, BODY, headerFor(BODY)));

        assertEquals("WEBHOOK_CLIENT_UNKNOWN", ex.code());
    }

    @Test
    void rejectsAMalformedHeader() {
        assertEquals("WEBHOOK_SIGNATURE_MALFORMED",
                assertThrows(UnauthorizedException.class,
                        () -> verifier.verify("eos-crawler", BODY, "v1=deadbeef")).code());
        assertEquals("WEBHOOK_SIGNATURE_MALFORMED",
                assertThrows(UnauthorizedException.class,
                        () -> verifier.verify("eos-crawler", BODY, null)).code());
        assertEquals("WEBHOOK_SIGNATURE_MALFORMED",
                assertThrows(UnauthorizedException.class,
                        () -> verifier.verify("eos-crawler", BODY, "t=notanumber,v1=deadbeef")).code());
    }

    private static String headerFor(String body) {
        long now = Instant.now().getEpochSecond();
        return "t=" + now + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, body);
    }
}
