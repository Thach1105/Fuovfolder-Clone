package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.PayloadTooLargeException;
import com.fuoverflow.common.exception.UnauthorizedException;
import com.fuoverflow.exam.api.dto.webhook.WebhookReceiptResponse;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.persistence.ExamWebhookEventEntity;
import com.fuoverflow.exam.persistence.ExamWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamWebhookReceiptServiceTest {

    private static final String SECRET = "s3cr3t";
    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC";

    @Mock private ExamWebhookEventRepository eventRepository;

    private ExamWebhookReceiptService service;
    private String body;
    private String header;

    @BeforeEach
    void setUp() {
        service = serviceWith(properties(null));
        body = validBody();
        header = headerFor(body);
    }

    @Test
    void storesANewEventAsPending() {
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-1"))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        WebhookReceiptResponse response = service.receive("eos-crawler", body, header);

        assertFalse(response.duplicate());
        assertEquals("queued", response.status());

        ArgumentCaptor<ExamWebhookEventEntity> saved =
                ArgumentCaptor.forClass(ExamWebhookEventEntity.class);
        verify(eventRepository).save(saved.capture());
        assertEquals("eos-crawler", saved.getValue().getClientId());
        assertEquals("evt-1", saved.getValue().getEventId());
        assertEquals("pending", saved.getValue().getStatus());
        assertTrue(saved.getValue().isSignatureValid());
        assertEquals(64, saved.getValue().getPayloadSha256().length());
    }

    @Test
    void returnsTheEarlierReceiptForADuplicateEventId() {
        ExamWebhookEventEntity existing = ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-1", "exam.paper.upserted",
                "{}", "b".repeat(64), true, Instant.now());
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-1"))
                .thenReturn(Optional.of(existing));

        WebhookReceiptResponse response = service.receive("eos-crawler", body, header);

        assertTrue(response.duplicate());
        assertEquals(existing.getId(), response.receiptId());
        assertEquals("queued", response.status());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void reportsTheStoredStatusOfAProcessedDuplicate() {
        ExamWebhookEventEntity existing = ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-1", "exam.paper.upserted",
                "{}", "b".repeat(64), true, Instant.now());
        existing.markProcessing();
        existing.markDone(UUID.randomUUID(), Instant.now());
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-1"))
                .thenReturn(Optional.of(existing));

        assertEquals("done", service.receive("eos-crawler", body, header).status());
    }

    @Test
    void rejectsABodyOverTheConfiguredCeilingBeforeParsing() {
        ExamWebhookReceiptService small = serviceWith(properties(10L));

        PayloadTooLargeException ex = assertThrows(PayloadTooLargeException.class,
                () -> small.receive("eos-crawler", body, header));

        assertEquals("WEBHOOK_TOO_LARGE", ex.code());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void rejectsABadSignatureBeforeTouchingTheDatabase() {
        assertThrows(UnauthorizedException.class,
                () -> service.receive("eos-crawler", body, headerFor("{\"other\":\"body\"}")));

        verify(eventRepository, never()).save(any());
    }

    @Test
    void rejectsMalformedJson() {
        String broken = "{\"eventId\":";

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.receive("eos-crawler", broken, headerFor(broken)));

        assertEquals("WEBHOOK_PAYLOAD_INVALID", ex.code());
    }

    @Test
    void rejectsAnUnprocessablePayloadAtTheEdge() {
        String noQuestions = """
                {"eventId":"evt-9","eventType":"exam.paper.upserted",
                 "sentAt":"2026-08-31T03:54:59Z",
                 "paper":{"examCode":"TEST_EOS_Client_1","paperType":"FE","subjectCode":"TEST",
                 "title":"t","questions":[]}}
                """;
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-9"))
                .thenReturn(Optional.empty());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.receive("eos-crawler", noQuestions, headerFor(noQuestions)));

        assertEquals("WEBHOOK_FE_QUESTIONS_REQUIRED", ex.code());
        verify(eventRepository, never()).save(any());
    }

    @Test
    void toleratesUnknownFieldsFromTheSender() {
        String extra = validBody().replace(
                "\"eventType\":\"exam.paper.upserted\"",
                "\"eventType\":\"exam.paper.upserted\",\"somethingNew\":{\"a\":1}");
        when(eventRepository.findByClientIdAndEventId("eos-crawler", "evt-1"))
                .thenReturn(Optional.empty());
        when(eventRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        assertFalse(service.receive("eos-crawler", extra, headerFor(extra)).duplicate());
    }

    private ExamWebhookReceiptService serviceWith(ExamWebhookProperties properties) {
        return new ExamWebhookReceiptService(
                new ExamWebhookSignatureVerifier(properties),
                new ExamWebhookPayloadValidator(5_242_880L, 200),
                eventRepository,
                properties,
                new ExamWebhookPayloadReader(
                        new ObjectMapper().registerModule(new JavaTimeModule()),
                        new EosPayloadAdapter()));
    }

    private static ExamWebhookProperties properties(Long maxPayloadBytes) {
        return new ExamWebhookProperties(Map.of("eos-crawler", SECRET),
                List.of("cdn.example.com"), maxPayloadBytes, null, null, null, null, null, null, null);
    }

    private static String validBody() {
        return """
                {"eventId":"evt-1","eventType":"exam.paper.upserted",
                 "sentAt":"2026-08-31T03:54:59Z",
                 "paper":{"examCode":"TEST_EOS_Client_1","paperType":"FE","subjectCode":"TEST",
                 "title":"t","questions":[{"externalId":"1","questionText":"stem",
                 "images":[{"sortOrder":0,"mimeType":"image/png","sizeBytes":70,
                 "sha256":"%s","contentBase64":"%s"}]}]}}
                """.formatted("a".repeat(64), PNG_BASE64);
    }

    private static String headerFor(String payload) {
        long now = Instant.now().getEpochSecond();
        return "t=" + now + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, payload);
    }
}
