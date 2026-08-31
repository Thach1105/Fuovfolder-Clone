package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminPaperResponse;
import com.fuoverflow.exam.api.dto.AdminWebhookEventResponse;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemEntity;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceEntity;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
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
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamPaperAdminServiceTest {

    @Mock private ExamPaperRepository paperRepository;
    @Mock private ExamFeQuestionRepository feQuestionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamPeResourceRepository peResourceRepository;
    @Mock private ExamWebhookEventRepository webhookEventRepository;
    @Mock private ExamMediaService mediaService;

    private ExamPaperAdminService service;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        service = new ExamPaperAdminService(
                paperRepository, feQuestionRepository, peItemRepository, peResourceRepository,
                webhookEventRepository, mediaService, new ObjectMapper());
        subjectId = UUID.randomUUID();
        lenient().when(paperRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(feQuestionRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(peItemRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(peResourceRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(any()))
                .thenReturn(List.of());
        lenient().when(peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(any()))
                .thenReturn(List.of());
    }

    @Test
    void publishRejectsAnFePaperWithNoQuestions() {
        ExamPaperEntity paper = draft(ExamPaperType.FE);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(feQuestionRepository.countByPaperIdAndDeletedAtIsNull(paper.getId())).thenReturn(0L);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.publish(paper.getId()));

        assertEquals("EXAM_PAPER_EMPTY", ex.code());
    }

    @Test
    void publishStampsPublishedAt() {
        ExamPaperEntity paper = draft(ExamPaperType.FE);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(feQuestionRepository.countByPaperIdAndDeletedAtIsNull(paper.getId())).thenReturn(50L);

        AdminPaperResponse response = service.publish(paper.getId());

        assertEquals("published", response.status());
        assertNotNull(response.publishedAt());
        assertTrue(paper.isPublished());
        verify(paperRepository).save(paper);
    }

    @Test
    void publishIsIdempotent() {
        ExamPaperEntity paper = draft(ExamPaperType.FE);
        Instant firstPublish = Instant.parse("2026-08-30T10:00:00Z");
        paper.publish(firstPublish);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        AdminPaperResponse response = service.publish(paper.getId());

        assertEquals(firstPublish, response.publishedAt());
    }

    @Test
    void publishRejectsAPePaperWithNeitherImagesNorResources() {
        ExamPaperEntity paper = draft(ExamPaperType.PE);
        ExamPeItemEntity item = ExamPeItemEntity.create(
                UUID.randomUUID(), subjectId, "PE", null, "[]", 0, Instant.now());
        item.setPaperId(paper.getId());
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paper.getId()))
                .thenReturn(List.of(item));
        when(peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()))
                .thenReturn(List.of());

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.publish(paper.getId()));

        assertEquals("EXAM_PAPER_EMPTY", ex.code());
    }

    @Test
    void publishAcceptsAPePaperThatOnlyHasResources() {
        ExamPaperEntity paper = draft(ExamPaperType.PE);
        ExamPeItemEntity item = ExamPeItemEntity.create(
                UUID.randomUUID(), subjectId, "PE", null, "[]", 0, Instant.now());
        item.setPaperId(paper.getId());
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paper.getId()))
                .thenReturn(List.of(item));
        when(peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()))
                .thenReturn(List.of(resource(item.getId())));

        assertEquals("published", service.publish(paper.getId()).status());
    }

    @Test
    void deleteSoftDeletesThePaperItsQuestionsAndTheirObjects() {
        ExamPaperEntity paper = draft(ExamPaperType.FE);
        ExamFeQuestionEntity question = ExamFeQuestionEntity.create(
                UUID.randomUUID(), subjectId, "Q", "[\"exam/fe/a.png\"]",
                "[\"exam/fe/a-blur.jpg\"]", 0, Instant.now(), paper.getId());
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paper.getId()))
                .thenReturn(new ArrayList<>(List.of(question)));

        service.delete(paper.getId());

        assertNotNull(paper.getDeletedAt());
        assertNotNull(question.getDeletedAt());
        verify(mediaService).deletePairedAll(
                eq(List.of("exam/fe/a.png")), eq(List.of("exam/fe/a-blur.jpg")));
    }

    @Test
    void deleteAlsoRemovesPeResources() {
        ExamPaperEntity paper = draft(ExamPaperType.PE);
        ExamPeItemEntity item = ExamPeItemEntity.create(
                UUID.randomUUID(), subjectId, "PE", null, "[\"exam/pe/a.png\"]", 0, Instant.now());
        item.setPaperId(paper.getId());
        ExamPeResourceEntity resource = resource(item.getId());
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paper.getId()))
                .thenReturn(new ArrayList<>(List.of(item)));
        when(peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()))
                .thenReturn(new ArrayList<>(List.of(resource)));

        service.delete(paper.getId());

        assertNotNull(item.getDeletedAt());
        assertNotNull(resource.getDeletedAt());
        verify(mediaService).deleteStoredReference("exam/pe/resources/a.zip");
    }

    @Test
    void deleteRejectsAnUnknownPaper() {
        UUID missing = UUID.randomUUID();
        when(paperRepository.findByIdAndDeletedAtIsNull(missing)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.delete(missing));
    }

    @Test
    void listFiltersByStatusWhenGiven() {
        when(paperRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc("draft"))
                .thenReturn(List.of(draft(ExamPaperType.FE)));

        List<AdminPaperResponse> papers = service.list(null, "draft");

        assertEquals(1, papers.size());
        assertEquals("draft", papers.get(0).status());
    }

    @Test
    void listFiltersBySubjectWhenGiven() {
        when(paperRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(subjectId))
                .thenReturn(List.of(draft(ExamPaperType.FE), draft(ExamPaperType.PE)));

        assertEquals(2, service.list(subjectId, null).size());
    }

    @Test
    void webhookEventIsReadBackWithItsError() {
        ExamWebhookEventEntity event = ExamWebhookEventEntity.received(
                UUID.randomUUID(), "eos-crawler", "evt-1", "exam.paper.upserted",
                "{}", "a".repeat(64), true, Instant.now());
        event.markProcessing();
        event.markFailed("WEBHOOK_RESOURCE_FETCH_FAILED", "không tải được", Instant.now());
        when(webhookEventRepository.findById(event.getId())).thenReturn(Optional.of(event));

        AdminWebhookEventResponse response = service.getWebhookEvent(event.getId());

        assertEquals("failed", response.status());
        assertEquals("WEBHOOK_RESOURCE_FETCH_FAILED", response.errorCode());
        assertEquals(1, response.attemptCount());
        assertEquals("eos-crawler", response.clientId());
    }

    @Test
    void webhookEventRejectsAnUnknownReceipt() {
        UUID missing = UUID.randomUUID();
        when(webhookEventRepository.findById(missing)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.getWebhookEvent(missing));
    }

    private ExamPaperEntity draft(ExamPaperType type) {
        return ExamPaperEntity.draft(
                UUID.randomUUID(), subjectId, type, "MLN111_SU26_" + type.dbValue() + "_1",
                "SU26", null, type.dbValue() + " paper", null, 60, null, 50,
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                "webhook:eos-crawler", "1", 0, Instant.now());
    }

    private static ExamPeResourceEntity resource(UUID itemId) {
        return ExamPeResourceEntity.create(
                UUID.randomUUID(), itemId, "Starter", "exam/pe/resources/a.zip",
                "a.zip", "application/zip", 10L, 0, Instant.now());
    }
}
