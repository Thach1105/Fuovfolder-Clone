package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.api.dto.PublicFeQuestionResponse;
import com.fuoverflow.exam.api.dto.PublicPaperDetailResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectDetailResponse;
import com.fuoverflow.exam.persistence.ExamCommentRepository;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamCatalogQueryServiceTest {
    @Mock private ExamSubjectRepository subjectRepository;
    @Mock private ExamPaperRepository paperRepository;
    @Mock private ExamFeQuestionRepository feQuestionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamPeResourceRepository peResourceRepository;
    @Mock private ExamCommentRepository commentRepository;
    @Mock private ExamAccessGuard accessGuard;
    @Mock private ExamMediaUrlResolver urlResolver;

    private ExamCatalogQueryService service;
    private UUID userId;
    private UUID subjectId;
    private UUID paperId;

    @BeforeEach
    void setUp() {
        service = new ExamCatalogQueryService(
                subjectRepository, paperRepository, feQuestionRepository,
                peItemRepository, peResourceRepository, commentRepository,
                accessGuard, urlResolver, new ObjectMapper());
        userId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        paperId = UUID.randomUUID();
        lenient().when(urlResolver.signed(any())).thenAnswer(inv -> "signed:" + inv.getArgument(0));
        lenient().when(urlResolver.signedAll(any())).thenReturn(List.of());
        lenient().when(commentRepository.countBySubjectTypeAndSubjectIdAndDeletedAtIsNull(any(), any()))
                .thenReturn(0L);
    }

    @Test
    void nonMember_allPostsVisible_previewBudgetSpentAcrossThePaper() {
        ExamSubjectEntity subject = subject(1);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(3, 2));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertTrue(res.locked());
        assertEquals(3, res.totalCount());
        assertEquals(3, res.questions().size());
        // The budget is one image for the whole paper, so only the very first is readable.
        assertEquals("full", res.questions().get(0).images().get(0).type());
        assertEquals("blur", res.questions().get(0).images().get(1).type());
        assertEquals(1, countByType(res, "full"));
        assertEquals(5, countByType(res, "blur"));
    }

    @Test
    void nonMember_oneImagePerQuestion_doesNotUnlockTheWholePaper() {
        ExamSubjectEntity subject = subject(2);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(50, 1));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertEquals(50, res.questions().size());
        assertEquals(2, countByType(res, "full"));
        assertEquals(48, countByType(res, "blur"));
    }

    @Test
    void member_oneImagePerQuestion_seesEverything() {
        ExamSubjectEntity subject = subject(2);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(true);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(50, 1));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertEquals(50, countByType(res, "full"));
        assertEquals(0, countByType(res, "blur"));
    }

    @Test
    void nonMember_previewSpansQuestionBoundaries() {
        ExamSubjectEntity subject = subject(3);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(3, 2));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertEquals("full", res.questions().get(0).images().get(0).type());
        assertEquals("full", res.questions().get(0).images().get(1).type());
        assertEquals("full", res.questions().get(1).images().get(0).type());
        assertEquals("blur", res.questions().get(1).images().get(1).type());
        assertEquals("blur", res.questions().get(2).images().get(0).type());
        assertEquals("blur", res.questions().get(2).images().get(1).type());
    }

    @Test
    void imageIndexStaysRelativeToItsOwnQuestion() {
        ExamSubjectEntity subject = subject(1);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(2, 2));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        // The lightbox keys off this index, so it must not become a paper-wide counter.
        assertEquals(0, res.questions().get(1).images().get(0).index());
        assertEquals(1, res.questions().get(1).images().get(1).index());
    }

    private static long countByType(PublicFeQuestionListResponse res, String type) {
        return res.questions().stream()
                .flatMap(q -> q.images().stream())
                .filter(img -> type.equals(img.type()))
                .count();
    }

    @Test
    void member_allPostsVisible_allImagesFull_unlocked() {
        ExamSubjectEntity subject = subject(1);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(true);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(3, 2));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertFalse(res.locked());
        assertEquals(3, res.totalCount());
        assertEquals(3, res.questions().size());
        for (PublicFeQuestionResponse q : res.questions()) {
            assertTrue(q.images().stream().allMatch(img -> "full".equals(img.type())));
        }
    }

    @Test
    void nonMember_previewCountLargerThanImages_allImagesFull() {
        ExamSubjectEntity subject = subject(5);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(2, 2));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertTrue(res.locked());
        assertEquals(2, res.questions().size());
        for (PublicFeQuestionResponse q : res.questions()) {
            assertTrue(q.images().stream().allMatch(img -> "full".equals(img.type())));
        }
    }

    @Test
    void subjectDetailListsOnlyPublishedPapers() {
        ExamSubjectEntity subject = subject(2);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(paperRepository.findBySubjectIdAndStatusAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(
                subjectId, "published")).thenReturn(List.of(publishedFePaper()));
        when(feQuestionRepository.countByPaperIdAndDeletedAtIsNull(any())).thenReturn(50L);

        PublicSubjectDetailResponse detail = service.getDetail("MLN111", userId);

        assertEquals(1, detail.papers().size());
        assertEquals("FE", detail.papers().get(0).type());
        assertEquals("SU26", detail.papers().get(0).term());
        assertEquals(50, detail.papers().get(0).imageCount());
        assertEquals(1, detail.fePaperCount());
        assertEquals(0, detail.pePaperCount());
    }

    @Test
    void relatedSubjectsExcludeTheCurrentOne() {
        ExamSubjectEntity subject = ExamSubjectEntity.create(
                subjectId, "MLN111", "Title", null, null, null, "chinh-tri", null,
                2, true, 0, Instant.now());
        ExamSubjectEntity sibling = ExamSubjectEntity.create(
                UUID.randomUUID(), "MLN122", "Other", null, null, null, "chinh-tri", null,
                2, true, 1, Instant.now());
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(subjectRepository.findTop6ByCategorySlugAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(
                "chinh-tri")).thenReturn(List.of(subject, sibling));

        PublicSubjectDetailResponse detail = service.getDetail("MLN111", userId);

        assertEquals(1, detail.related().size());
        assertEquals("MLN122", detail.related().get(0).code());
    }

    @Test
    void relatedIsEmptyWithoutACategory() {
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111"))
                .thenReturn(Optional.of(subject(2)));

        assertTrue(service.getDetail("MLN111", userId).related().isEmpty());
    }

    @Test
    void paperDetailRequiresMembership() {
        UUID paperId = UUID.randomUUID();
        org.mockito.Mockito.doThrow(new ForbiddenException("NO_ACTIVE_MEMBERSHIP", "no"))
                .when(accessGuard).requireActiveMembership(userId);

        assertThrows(ForbiddenException.class, () -> service.getPaper(paperId, userId));
    }

    @Test
    void paperDetailRejectsADraft() {
        ExamPaperEntity draft = draftFePaper();
        when(paperRepository.findByIdAndDeletedAtIsNull(draft.getId())).thenReturn(Optional.of(draft));

        assertThrows(NotFoundException.class, () -> service.getPaper(draft.getId(), userId));
    }

    @Test
    void paperDetailReturnsSignedImagesOfEveryQuestion() {
        ExamPaperEntity paper = publishedFePaper();
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(subjectRepository.findByIdAndDeletedAtIsNull(subjectId)).thenReturn(Optional.of(subject(2)));
        when(feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paper.getId()))
                .thenReturn(questionsWithImages(2, 2));
        when(urlResolver.signedAll(any())).thenAnswer(inv -> {
            List<String> keys = inv.getArgument(0);
            return keys.stream().map(k -> "signed:" + k).toList();
        });

        PublicPaperDetailResponse detail = service.getPaper(paper.getId(), userId);

        assertEquals("FE", detail.type());
        assertEquals("MLN111", detail.subjectCode());
        assertEquals(4, detail.imageUrls().size());
        assertTrue(detail.imageUrls().get(0).startsWith("signed:"));
        assertTrue(detail.resources().isEmpty());
    }

    private ExamPaperEntity publishedFePaper() {
        ExamPaperEntity paper = draftFePaper();
        paper.publish(Instant.now());
        return paper;
    }

    private ExamPaperEntity draftFePaper() {
        return ExamPaperEntity.draft(
                UUID.randomUUID(), subjectId, ExamPaperType.FE, "MLN111_SU26_FE_1",
                "SU26", null, "MLN111 FE", null, 60, null, 50,
                "a".repeat(64), "webhook:eos-crawler", "1", 0, Instant.now());
    }

    @Test
    void listPeItems_requiresMembership() {
        ExamSubjectEntity subject = subject(3);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        org.mockito.Mockito.doThrow(new ForbiddenException("NO_ACTIVE_MEMBERSHIP", "no"))
                .when(accessGuard).requireActiveMembership(userId);

        assertThrows(ForbiddenException.class, () -> service.listPeItems("MLN111", userId));
    }

    private ExamSubjectEntity subject(int previewImageCount) {
        return ExamSubjectEntity.create(
                subjectId, "MLN111", "Title", null, null, null, null, null,
                previewImageCount, true, 0, Instant.now());
    }

    private List<ExamFeQuestionEntity> questionsWithImages(int questionCount, int imagesPerQuestion) {
        List<ExamFeQuestionEntity> list = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < questionCount; i++) {
            String imageJson = imagesJson(imagesPerQuestion, "img");
            String blurJson = imagesJson(imagesPerQuestion, "blur");
            list.add(ExamFeQuestionEntity.create(
                    UUID.randomUUID(), subjectId, "Q" + i, imageJson, blurJson, i, now, paperId));
        }
        return list;
    }

    private static String imagesJson(int count, String prefix) {
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < count; i++) {
            if (i > 0) sb.append(',');
            sb.append('"').append(prefix).append(i).append('"');
        }
        return sb.append(']').toString();
    }
}
