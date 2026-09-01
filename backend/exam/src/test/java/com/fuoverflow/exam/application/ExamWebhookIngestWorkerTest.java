package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.persistence.ExamWebhookEventEntity;
import com.fuoverflow.exam.persistence.ExamWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamWebhookIngestWorkerTest {

    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC";

    @Mock private ExamWebhookEventRepository eventRepository;
    @Mock private ExamPaperIngestService ingestService;

    private ExamWebhookIngestWorker worker;
    private ExamWebhookEventEntity event;

    @BeforeEach
    void setUp() {
        worker = newWorker(5);
        event = pendingEvent();
        lenient().when(eventRepository.findTop20ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc(
                anyString(), any())).thenReturn(List.of(event));
        lenient().when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void marksTheEventDoneAndStripsBase64FromTheStoredPayload() {
        UUID paperId = UUID.randomUUID();
        when(ingestService.ingest(any(), anyString()))
                .thenReturn(new ExamPaperIngestService.IngestOutcome(
                        paperId, ExamPaperIngestService.Outcome.CREATED));

        worker.processPending();

        assertEquals("done", event.getStatus());
        assertEquals(paperId, event.getPaperId());
        assertEquals(1, event.getAttemptCount());
        assertFalse(event.getPayloadJson().contains(PNG_BASE64),
                "stored payload must not keep the image bytes");
        assertTrue(event.getPayloadJson().contains("\"sha256\""),
                "hashes stay so the row is still useful for audit");
        assertTrue(event.getPayloadJson().contains("\"contentBase64\":null"));
    }

    @Test
    void passesTheIngestSourceDerivedFromTheClient() {
        when(ingestService.ingest(any(), anyString()))
                .thenReturn(new ExamPaperIngestService.IngestOutcome(
                        UUID.randomUUID(), ExamPaperIngestService.Outcome.CREATED));

        worker.processPending();

        verify(ingestService).ingest(any(), org.mockito.ArgumentMatchers.eq("webhook:eos-crawler"));
    }

    @Test
    void reschedulesWithBackoffOnATransientFailure() {
        when(ingestService.ingest(any(), anyString()))
                .thenThrow(new IllegalStateException("storage down"));

        worker.processPending();

        assertEquals("pending", event.getStatus());
        assertEquals(1, event.getAttemptCount());
        assertTrue(event.getAvailableAt().isAfter(Instant.now()));
        assertNull(event.getProcessedAt());
    }

    @Test
    void failsPermanentlyOnAPayloadError() {
        when(ingestService.ingest(any(), anyString()))
                .thenThrow(new BadRequestException("WEBHOOK_IMAGE_INVALID_BASE64", "bad"));

        worker.processPending();

        assertEquals("failed", event.getStatus());
        assertEquals("WEBHOOK_IMAGE_INVALID_BASE64", event.getErrorCode());
    }

    @Test
    void failsPermanentlyOnceAttemptsAreExhausted() {
        worker = newWorker(1);
        when(ingestService.ingest(any(), anyString()))
                .thenThrow(new IllegalStateException("storage down"));

        worker.processPending();

        assertEquals("failed", event.getStatus());
        assertEquals("WEBHOOK_INGEST_FAILED", event.getErrorCode());
    }

    @Test
    void aPoisonedEventDoesNotStopTheRestOfTheQueue() {
        ExamWebhookEventEntity broken = ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-broken", "exam.paper.upserted",
                "{not json", "b".repeat(64), true, Instant.now());
        when(eventRepository.findTop20ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc(
                anyString(), any())).thenReturn(List.of(broken, event));
        when(ingestService.ingest(any(), anyString()))
                .thenReturn(new ExamPaperIngestService.IngestOutcome(
                        UUID.randomUUID(), ExamPaperIngestService.Outcome.CREATED));

        worker.processPending();

        assertEquals("failed", broken.getStatus());
        assertEquals("done", event.getStatus());
    }

    @Test
    void doesNothingWhenTheQueueIsEmpty() {
        when(eventRepository.findTop20ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc(
                anyString(), any())).thenReturn(List.of());

        worker.processPending();

        verify(ingestService, never()).ingest(any(), anyString());
        verify(eventRepository, never()).save(any());
    }

    private ExamWebhookIngestWorker newWorker(int maxAttempts) {
        ExamWebhookProperties properties = new ExamWebhookProperties(
                Map.of("eos-crawler", "s3cr3t"), List.of("cdn.example.com"),
                null, null, null, null, maxAttempts, null, null);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        return new ExamWebhookIngestWorker(
                eventRepository,
                new ExamWebhookPayloadValidator(5_242_880L, 200),
                new ExamWebhookPayloadReader(objectMapper, new EosPayloadAdapter()),
                ingestService,
                properties,
                objectMapper);
    }

    private static ExamWebhookEventEntity pendingEvent() {
        String payload = """
                {"eventId":"evt-1","eventType":"exam.paper.upserted",
                 "sentAt":"2026-08-31T03:54:59Z",
                 "paper":{"examCode":"SCM302_SU26_FE_553972","paperType":"FE","subjectCode":"SCM302",
                 "term":"SU26","title":"SCM302 FE","questions":[{"externalId":"1","questionText":"stem",
                 "images":[{"sortOrder":0,"mimeType":"image/png","sizeBytes":69,
                 "sha256":"%s","contentBase64":"%s"}]}]}}
                """.formatted("a".repeat(64), PNG_BASE64);
        return ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-1", "exam.paper.upserted",
                payload, "a".repeat(64), true, Instant.now());
    }
}
