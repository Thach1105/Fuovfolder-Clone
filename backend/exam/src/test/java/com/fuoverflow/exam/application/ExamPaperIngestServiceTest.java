package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.exam.config.ExamProperties;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.domain.IngestAsset;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.domain.IngestQuestion;
import com.fuoverflow.exam.domain.IngestResource;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemEntity;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceEntity;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import com.fuoverflow.exam.support.ExamPaperFingerprint;
import com.fuoverflow.material.domain.UploadPurpose;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamPaperIngestServiceTest {

    @Mock private ExamSubjectRepository subjectRepository;
    @Mock private ExamPaperRepository paperRepository;
    @Mock private ExamFeQuestionRepository feQuestionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamPeResourceRepository peResourceRepository;
    @Mock private ExamIngestStorage ingestStorage;
    @Mock private ExamResourceFetcher resourceFetcher;
    @Mock private ExamMediaService mediaService;

    private ExamPaperIngestService service;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        service = new ExamPaperIngestService(
                subjectRepository, paperRepository, feQuestionRepository,
                peItemRepository, peResourceRepository, ingestStorage, resourceFetcher,
                mediaService, new ExamProperties(null, null, 2), new ObjectMapper());
        subjectId = UUID.randomUUID();

        lenient().when(subjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paperRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(feQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(peItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(peResourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(paperRepository.findByExamCodeIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.empty());
        lenient().when(feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(any()))
                .thenReturn(List.of());
        lenient().when(peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(any()))
                .thenReturn(List.of());
        lenient().when(ingestStorage.storeImage(any(), anyString(), any(), anyBoolean()))
                .thenAnswer(inv -> {
                    UploadPurpose purpose = inv.getArgument(2);
                    boolean withBlur = inv.getArgument(3);
                    String key = purpose.folder() + "/" + UUID.randomUUID() + ".png";
                    return new ExamIngestStorage.StoredImage(key, withBlur ? key + "-blur.jpg" : null);
                });
    }

    @Test
    void createsAnInactiveSubjectWhenTheCodeIsUnknown() {
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("SCM302"))
                .thenReturn(Optional.empty());

        service.ingest(fePaper(1), "webhook:eos-crawler");

        ArgumentCaptor<ExamSubjectEntity> saved = ArgumentCaptor.forClass(ExamSubjectEntity.class);
        verify(subjectRepository).save(saved.capture());
        assertFalse(saved.getValue().isActive(), "a subject with no title must not reach the catalog");
        assertEquals("SCM302", saved.getValue().getCode());
        assertEquals(2, saved.getValue().getFePreviewImageCount());
    }

    @Test
    void reusesAnExistingSubject() {
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("SCM302"))
                .thenReturn(Optional.of(subject()));

        service.ingest(fePaper(1), "webhook:eos-crawler");

        verify(subjectRepository, never()).save(any());
    }

    @Test
    void createsADraftPaperWithOnePostPerQuestion() {
        stubSubject();

        ExamPaperIngestService.IngestOutcome outcome =
                service.ingest(fePaper(3, "HCM"), "webhook:eos-crawler");

        assertEquals(ExamPaperIngestService.Outcome.CREATED, outcome.outcome());
        assertNotNull(outcome.paperId());

        ArgumentCaptor<ExamPaperEntity> paper = ArgumentCaptor.forClass(ExamPaperEntity.class);
        verify(paperRepository).save(paper.capture());
        assertEquals("draft", paper.getValue().getStatus());
        assertEquals("FE", paper.getValue().getPaperType());
        assertEquals("SCM302_SU26_FE_553972", paper.getValue().getExamCode());
        assertEquals("SU26", paper.getValue().getTerm());
        assertEquals("webhook:eos-crawler", paper.getValue().getIngestSource());
        assertEquals("HCM", paper.getValue().getCampus());

        ArgumentCaptor<ExamFeQuestionEntity> questions =
                ArgumentCaptor.forClass(ExamFeQuestionEntity.class);
        verify(feQuestionRepository, times(3)).save(questions.capture());
        List<ExamFeQuestionEntity> saved = questions.getAllValues();
        assertEquals(outcome.paperId(), saved.get(0).getPaperId());
        assertEquals(0, saved.get(0).getSortOrder());
        assertEquals(2, saved.get(2).getSortOrder());
        assertTrue(saved.get(0).getQuestionImageUrls().contains("exam/fe/"));
        assertTrue(saved.get(0).getQuestionBlurUrls().contains("-blur.jpg"));
        verify(ingestStorage, times(3))
                .storeImage(any(), eq("image/png"), eq(UploadPurpose.EXAM_FE_IMAGE), eq(true));
    }

    @Test
    void updatesTheExistingDraftWhenTheFingerprintMatches() {
        stubSubject();
        IngestPaper incoming = fePaper(2);
        ExamPaperEntity existing = draftPaper(ExamPaperFingerprint.of(incoming));
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(existing.getFingerprint()))
                .thenReturn(Optional.of(existing));

        ExamFeQuestionEntity stale = ExamFeQuestionEntity.create(
                UUID.randomUUID(), subjectId, "old", "[\"exam/fe/old.png\"]",
                "[\"exam/fe/old-blur.jpg\"]", 0, Instant.now(), existing.getId());
        when(feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(existing.getId()))
                .thenReturn(new ArrayList<>(List.of(stale)));

        ExamPaperIngestService.IngestOutcome outcome = service.ingest(incoming, "webhook:eos-crawler");

        assertEquals(ExamPaperIngestService.Outcome.UPDATED, outcome.outcome());
        assertEquals(existing.getId(), outcome.paperId());
        assertNotNull(stale.getDeletedAt(), "the replaced question must be soft-deleted");
        verify(mediaService).deletePairedAll(
                eq(List.of("exam/fe/old.png")), eq(List.of("exam/fe/old-blur.jpg")));
        verify(feQuestionRepository, times(3)).save(any());
    }

    @Test
    void adoptsAnExistingDraftThatSharesTheExamCode() {
        stubSubject();
        IngestPaper incoming = fePaper(1);
        ExamPaperEntity existing = draftPaper("d".repeat(64));
        when(paperRepository.findByExamCodeIgnoreCaseAndDeletedAtIsNull("SCM302_SU26_FE_553972"))
                .thenReturn(Optional.of(existing));

        ExamPaperIngestService.IngestOutcome outcome = service.ingest(incoming, "webhook:eos-crawler");

        assertEquals(ExamPaperIngestService.Outcome.UPDATED, outcome.outcome());
        assertEquals(existing.getId(), outcome.paperId());
        assertEquals(ExamPaperFingerprint.of(incoming), existing.getFingerprint(),
                "adopting must refresh the fingerprint so the unique index stays consistent");
    }

    @Test
    void skipsAPaperThatIsAlreadyPublished() {
        stubSubject();
        IngestPaper incoming = fePaper(1);
        ExamPaperEntity published = draftPaper(ExamPaperFingerprint.of(incoming));
        published.publish(Instant.now());
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(published.getFingerprint()))
                .thenReturn(Optional.of(published));

        ExamPaperIngestService.IngestOutcome outcome = service.ingest(incoming, "webhook:eos-crawler");

        assertEquals(ExamPaperIngestService.Outcome.SKIPPED_PUBLISHED, outcome.outcome());
        assertEquals(published.getId(), outcome.paperId());
        verify(feQuestionRepository, never()).save(any());
        verify(ingestStorage, never()).storeImage(any(), anyString(), any(), anyBoolean());
    }

    @Test
    void deletesTheDraftAndUploadedObjectsWhenStorageFails() {
        stubSubject();
        // doReturn/doThrow, not when(...): the latter would invoke the stub installed in setUp
        // with null arguments while building the matcher.
        doReturn(new ExamIngestStorage.StoredImage("exam/fe/first.png", "exam/fe/first-blur.jpg"))
                .doThrow(new RuntimeException("disk full"))
                .when(ingestStorage).storeImage(any(), anyString(), any(), anyBoolean());

        assertThrows(RuntimeException.class, () -> service.ingest(fePaper(2), "webhook:eos-crawler"));

        verify(mediaService).deletePairedAll(
                eq(List.of("exam/fe/first.png")), eq(List.of("exam/fe/first-blur.jpg")));
        verify(paperRepository).delete(any(ExamPaperEntity.class));
    }

    @Test
    void createsAPeItemWithImagesAndFetchedResources() {
        stubSubject();
        when(resourceFetcher.fetch(any())).thenReturn("PKzip".getBytes());
        when(ingestStorage.storeResource(any(), anyString(), anyString()))
                .thenReturn("exam/pe/resources/x.zip");

        ExamPaperIngestService.IngestOutcome outcome = service.ingest(pePaper(), "webhook:eos-crawler");

        assertEquals(ExamPaperIngestService.Outcome.CREATED, outcome.outcome());

        ArgumentCaptor<ExamPeItemEntity> item = ArgumentCaptor.forClass(ExamPeItemEntity.class);
        verify(peItemRepository).save(item.capture());
        assertEquals(outcome.paperId(), item.getValue().getPaperId());
        assertTrue(item.getValue().getExamImageUrls().contains("exam/pe/"));

        ArgumentCaptor<ExamPeResourceEntity> resource = ArgumentCaptor.forClass(ExamPeResourceEntity.class);
        verify(peResourceRepository).save(resource.capture());
        assertEquals("exam/pe/resources/x.zip", resource.getValue().getObjectKey());
        assertEquals("PE01_starter.zip", resource.getValue().getOriginalFilename());
        verify(ingestStorage)
                .storeImage(any(), eq("image/png"), eq(UploadPurpose.EXAM_PE_IMAGE), eq(false));
    }

    // --- fixtures -------------------------------------------------------------

    private void stubSubject() {
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(anyString()))
                .thenReturn(Optional.of(subject()));
    }

    private ExamSubjectEntity subject() {
        return ExamSubjectEntity.create(
                subjectId, "SCM302", "Supply Chain", null, null, null, null,
                2, true, 0, Instant.now());
    }

    private ExamPaperEntity draftPaper(String fingerprint) {
        return ExamPaperEntity.draft(
                UUID.randomUUID(), subjectId, ExamPaperType.FE, "SCM302_SU26_FE_553972",
                "SU26", null, "SCM302 FE", null, 60, new BigDecimal("50.00"), 50,
                fingerprint, "webhook:eos-crawler", "553972", 0, null, Instant.now());
    }

    private static IngestPaper fePaper(int questionCount) {
        return fePaper(questionCount, null);
    }

    private static IngestPaper fePaper(int questionCount, String campus) {
        List<IngestQuestion> questions = new ArrayList<>();
        for (int i = 0; i < questionCount; i++) {
            questions.add(new IngestQuestion(
                    "q" + i, i + 1, null, 1, 11001, BigDecimal.ONE,
                    List.of(new IngestAsset(0, "image/png", 10, "a".repeat(63) + i, new byte[]{1})),
                    List.of((long) i)));
        }
        return new IngestPaper(
                "SCM302_SU26_FE_553972", ExamPaperType.FE, "SCM302", "SU26", null,
                "SCM302 FE", null, 60, new BigDecimal("50.00"), questionCount,
                "eos-crawler", "553972", questions, List.of(), List.of(), campus);
    }

    private static IngestPaper pePaper() {
        return new IngestPaper(
                "PRJ301_SU26_PE_1", ExamPaperType.PE, "PRJ301", "SU26", null,
                "PE 1", "mô tả", null, null, null, "eos-crawler", "1",
                List.of(),
                List.of(new IngestAsset(0, "image/png", 10, "b".repeat(64), new byte[]{1})),
                List.of(new IngestResource(0, "Starter", "PE01_starter.zip",
                        "application/zip", 5, "c".repeat(64), "https://cdn.example.com/a.zip")), null);
    }
}
