package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.exam.api.dto.webhook.WebhookPaperReceipt;
import com.fuoverflow.exam.config.ExamProperties;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import com.fuoverflow.exam.persistence.ExamWebhookEventEntity;
import com.fuoverflow.exam.persistence.ExamWebhookEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.atLeastOnce;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Drives the whole ingest chain — signature, receipt, worker, storage — over a real EOS payload
 * (three questions of {@code SCM302_SU26_FE_553972}) with only the repositories and object storage
 * mocked. The exam module has no Spring integration harness, so this proves the wiring and the
 * image handling rather than the SQL; the migration itself is verified by booting the app.
 */
@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamPaperWebhookIngestFlowTest {

    private static final String SECRET = "s3cr3t";

    @Mock private ExamWebhookEventRepository eventRepository;
    @Mock private ExamSubjectRepository subjectRepository;
    @Mock private ExamPaperRepository paperRepository;
    @Mock private ExamFeQuestionRepository feQuestionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamPeResourceRepository peResourceRepository;
    @Mock private ObjectStorage objectStorage;
    @Mock private ExamMediaService mediaService;

    private ExamWebhookReceiptService receiptService;
    private ExamWebhookIngestWorker worker;
    private ExamPaperRepository capturedPapers;
    private final Map<UUID, ExamWebhookEventEntity> stored = new HashMap<>();
    private String body;

    @BeforeEach
    void setUp() throws Exception {
        ExamWebhookProperties webhookProperties = new ExamWebhookProperties(
                Map.of("eos-crawler", SECRET), List.of("cdn.example.com"),
                null, null, null, null, null, null, null, null);
        ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
        ExamWebhookPayloadValidator validator = new ExamWebhookPayloadValidator(5_242_880L, 200);

        ExamWebhookPayloadReader payloadReader =
                new ExamWebhookPayloadReader(objectMapper, new EosPayloadAdapter());
        receiptService = new ExamWebhookReceiptService(
                new ExamWebhookSignatureVerifier(webhookProperties),
                validator, eventRepository, webhookProperties, payloadReader);

        ExamPaperIngestService ingestService = new ExamPaperIngestService(
                subjectRepository, paperRepository, feQuestionRepository,
                peItemRepository, peResourceRepository,
                new ExamIngestStorage(objectStorage, new BlurImageGenerator()),
                new ExamResourceFetcher(webhookProperties),
                mediaService, new ExamProperties(null, null, 2), objectMapper);

        worker = new ExamWebhookIngestWorker(
                eventRepository, validator, payloadReader, ingestService,
                webhookProperties, objectMapper);
        capturedPapers = paperRepository;

        body = Files.readString(Path.of(
                getClass().getResource("/fixtures/fe-paper-webhook.json").toURI()),
                StandardCharsets.UTF_8);

        lenient().when(eventRepository.save(any())).thenAnswer(inv -> {
            ExamWebhookEventEntity event = inv.getArgument(0);
            stored.put(event.getId(), event);
            return event;
        });
        lenient().when(eventRepository.findByClientIdAndEventId(anyString(), anyString()))
                .thenAnswer(inv -> stored.values().stream()
                        .filter(e -> e.getClientId().equals(inv.getArgument(0))
                                && e.getEventId().equals(inv.getArgument(1)))
                        .findFirst());
        lenient().when(eventRepository.findTop20ByStatusAndAvailableAtBeforeOrderByAvailableAtAsc(
                        anyString(), any()))
                .thenAnswer(inv -> stored.values().stream()
                        .filter(e -> e.getStatus().equals(inv.getArgument(0)))
                        .toList());
        lenient().when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(subjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paperRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(paperRepository.findByExamCodeIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(feQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(any()))
                .thenReturn(List.of());
    }

    @Test
    void ingestsThreeRealQuestionsIntoADraftPaperWithBlurredSidecars() {
        WebhookPaperReceipt receipt = receiptService.receive("eos-crawler", body, headerFor(body)).results().get(0);
        worker.processPending();

        assertFalse(receipt.duplicate());
        assertEquals("queued", receipt.status());

        ExamWebhookEventEntity event = stored.get(receipt.receiptId());
        assertEquals("done", event.getStatus());
        assertNotNull(event.getPaperId());

        ArgumentCaptor<ExamSubjectEntity> subject = ArgumentCaptor.forClass(ExamSubjectEntity.class);
        verify(subjectRepository).save(subject.capture());
        assertEquals("SCM302", subject.getValue().getCode());
        assertFalse(subject.getValue().isActive());

        ArgumentCaptor<ExamPaperEntity> paper = ArgumentCaptor.forClass(ExamPaperEntity.class);
        verify(capturedPapers, atLeastOnce()).save(paper.capture());
        assertEquals("draft", paper.getValue().getStatus());
        assertEquals("SCM302_SU26_FE_553972", paper.getValue().getExamCode());
        assertEquals("SU26", paper.getValue().getTerm());
        assertEquals(60, paper.getValue().getDurationMinutes());

        ArgumentCaptor<ExamFeQuestionEntity> questions =
                ArgumentCaptor.forClass(ExamFeQuestionEntity.class);
        verify(feQuestionRepository, times(3)).save(questions.capture());
        for (ExamFeQuestionEntity question : questions.getAllValues()) {
            assertEquals(event.getPaperId(), question.getPaperId());
            assertTrue(question.getQuestionImageUrls().contains("exam/fe/"));
            assertTrue(question.getQuestionBlurUrls().contains("-blur.jpg"),
                    "each real PNG must produce a blur sidecar");
        }
        // The "(Choose N answer)" marker is all the text these questions carry.
        assertTrue(questions.getAllValues().stream()
                .allMatch(q -> q.getQuestionText() == null));

        // 3 originals + 3 blurs.
        verify(objectStorage, times(6)).storeBytes(any(), anyString(), anyString());
    }

    @Test
    void replayingTheSameBodyDoesNotCreateASecondPaper() {
        String header = headerFor(body);
        WebhookPaperReceipt first = receiptService.receive("eos-crawler", body, header).results().get(0);
        worker.processPending();

        WebhookPaperReceipt second = receiptService.receive("eos-crawler", body, headerFor(body)).results().get(0);

        assertTrue(second.duplicate());
        assertEquals(first.receiptId(), second.receiptId());
        assertEquals("done", second.status());
        assertEquals(1, stored.size());
        verify(feQuestionRepository, times(3)).save(any());
    }

    @Test
    void storedPayloadLosesTheImageBytesOnceProcessed() {
        WebhookPaperReceipt receipt = receiptService.receive("eos-crawler", body, headerFor(body)).results().get(0);
        worker.processPending();

        String payload = stored.get(receipt.receiptId()).getPayloadJson();
        assertTrue(payload.contains("\"contentBase64\":null"));
        assertFalse(payload.contains("iVBORw0KGgo"), "no base64 image data may remain");
        assertTrue(payload.length() < body.length() / 4,
                "stripping must shrink the row substantially");
    }

    @Test
    void everyImageIsStoredUnderTheFeFolderWithADatePrefix() {
        receiptService.receive("eos-crawler", body, headerFor(body));
        worker.processPending();

        ArgumentCaptor<String> keys = ArgumentCaptor.forClass(String.class);
        verify(objectStorage, atLeastOnce()).storeBytes(any(), keys.capture(), anyString());
        List<String> originals = new ArrayList<>(keys.getAllValues());
        assertTrue(originals.stream().allMatch(key -> key.startsWith("exam/fe/")));
        assertEquals(3, originals.stream().filter(key -> key.endsWith(".png")).count());
        assertEquals(3, originals.stream().filter(key -> key.endsWith("-blur.jpg")).count());
    }

    @Test
    void ingestsARawEosFileWithNoConversionByTheSender() throws Exception {
        String eos = Files.readString(Path.of(
                getClass().getResource("/fixtures/eos-raw-paper.json").toURI()),
                StandardCharsets.UTF_8);

        WebhookPaperReceipt receipt = receiptService.receive("eos-crawler", eos, headerFor(eos)).results().get(0);
        worker.processPending();

        assertFalse(receipt.duplicate());
        ExamWebhookEventEntity event = stored.get(receipt.receiptId());
        assertEquals("done", event.getStatus());
        assertTrue(event.getEventId().startsWith("eos:SCM302_SU26_FE_553972:"), event.getEventId());

        ArgumentCaptor<ExamPaperEntity> paper = ArgumentCaptor.forClass(ExamPaperEntity.class);
        verify(capturedPapers, atLeastOnce()).save(paper.capture());
        assertEquals("SCM302_SU26_FE_553972", paper.getValue().getExamCode());
        assertEquals("SU26", paper.getValue().getTerm());
        assertEquals("FE", paper.getValue().getPaperType());
        assertEquals("draft", paper.getValue().getStatus());
        assertEquals(60, paper.getValue().getDurationMinutes());

        verify(feQuestionRepository, times(3)).save(any());
        // 3 ảnh gốc + 3 ảnh blur, đúng như đường canonical.
        verify(objectStorage, times(6)).storeBytes(any(), anyString(), anyString());
    }

    @Test
    void storedEosPayloadLosesItsImageDataOnceProcessed() throws Exception {
        String eos = Files.readString(Path.of(
                getClass().getResource("/fixtures/eos-raw-paper.json").toURI()),
                StandardCharsets.UTF_8);

        WebhookPaperReceipt receipt = receiptService.receive("eos-crawler", eos, headerFor(eos)).results().get(0);
        worker.processPending();

        String payload = stored.get(receipt.receiptId()).getPayloadJson();
        assertTrue(payload.contains("\"ImageData\":null"));
        assertFalse(payload.contains("iVBORw0KGgo"), "no base64 image data may remain");
        assertTrue(payload.length() < eos.length() / 4, "stripping must shrink the row");
    }

    @Test
    void replayingTheSameEosFileIsANoOp() throws Exception {
        String eos = Files.readString(Path.of(
                getClass().getResource("/fixtures/eos-raw-paper.json").toURI()),
                StandardCharsets.UTF_8);

        WebhookPaperReceipt first = receiptService.receive("eos-crawler", eos, headerFor(eos)).results().get(0);
        worker.processPending();
        WebhookPaperReceipt second = receiptService.receive("eos-crawler", eos, headerFor(eos)).results().get(0);

        assertTrue(second.duplicate(), "cùng một file phải cho cùng eventId");
        assertEquals(first.receiptId(), second.receiptId());
        assertEquals(1, stored.size());
        verify(feQuestionRepository, times(3)).save(any());
    }

    private static String headerFor(String payload) {
        long now = Instant.now().getEpochSecond();
        return "t=" + now + ",v1=" + ExamWebhookSignatureVerifier.sign(SECRET, now, payload);
    }
}
