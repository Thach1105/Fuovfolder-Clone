package com.fuoverflow.exam.persistence;

import com.fuoverflow.exam.domain.ExamPaperStatus;
import com.fuoverflow.exam.domain.ExamPaperType;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamPaperEntityTest {

    @Test
    void draftStartsUnpublished() {
        ExamPaperEntity paper = newDraft();

        assertEquals(ExamPaperStatus.DRAFT.dbValue(), paper.getStatus());
        assertFalse(paper.isPublished());
        assertNull(paper.getPublishedAt());
        assertEquals(ExamPaperType.FE.dbValue(), paper.getPaperType());
    }

    @Test
    void publishStampsTimestampAndStatus() {
        ExamPaperEntity paper = newDraft();
        Instant at = Instant.parse("2026-08-31T10:00:00Z");

        paper.publish(at);

        assertTrue(paper.isPublished());
        assertEquals(at, paper.getPublishedAt());
        assertEquals(ExamPaperStatus.PUBLISHED.dbValue(), paper.getStatus());
    }

    @Test
    void webhookEventStartsPendingAndTracksAttempts() {
        ExamWebhookEventEntity event = ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-1", "exam.paper.upserted",
                "{}", "a".repeat(64), true, Instant.parse("2026-08-31T10:00:00Z"));

        assertEquals("pending", event.getStatus());
        assertEquals(0, event.getAttemptCount());

        event.markProcessing();
        assertEquals("processing", event.getStatus());
        assertEquals(1, event.getAttemptCount());

        Instant later = Instant.parse("2026-08-31T10:05:00Z");
        UUID paperId = UUID.randomUUID();
        event.markDone(paperId, later);

        assertEquals("done", event.getStatus());
        assertEquals(paperId, event.getPaperId());
        assertEquals(later, event.getProcessedAt());
    }

    @Test
    void webhookEventRetryGoesBackToPendingWithoutLosingAttempts() {
        ExamWebhookEventEntity event = ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-2", "exam.paper.upserted",
                "{}", "b".repeat(64), true, Instant.now());
        event.markProcessing();

        Instant retryAt = Instant.parse("2026-08-31T10:10:00Z");
        event.retryAt(retryAt);

        assertEquals("pending", event.getStatus());
        assertEquals(retryAt, event.getAvailableAt());
        assertEquals(1, event.getAttemptCount());
        assertNull(event.getProcessedAt());
    }

    @Test
    void webhookEventFailureKeepsTheReason() {
        ExamWebhookEventEntity event = ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-3", "exam.paper.upserted",
                "{}", "c".repeat(64), true, Instant.now());

        Instant at = Instant.parse("2026-08-31T10:20:00Z");
        event.markFailed("WEBHOOK_IMAGE_INVALID_BASE64", "ảnh không phải base64", at);

        assertEquals("failed", event.getStatus());
        assertEquals("WEBHOOK_IMAGE_INVALID_BASE64", event.getErrorCode());
        assertEquals(at, event.getProcessedAt());
    }

    private static ExamPaperEntity newDraft() {
        return ExamPaperEntity.draft(
                UUID.randomUUID(), UUID.randomUUID(), ExamPaperType.FE,
                "SCM302_SU26_FE_553972", "SU26", null, "SCM302 FE", null,
                60, new BigDecimal("50.00"), 50, "a".repeat(64),
                "webhook:eos-crawler", "553972", 0, Instant.now());
    }
}
