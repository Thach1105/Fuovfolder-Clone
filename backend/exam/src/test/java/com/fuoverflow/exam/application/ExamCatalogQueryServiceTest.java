package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.api.dto.PublicFeQuestionResponse;
import com.fuoverflow.exam.persistence.ExamCommentRepository;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
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
                subjectRepository, feQuestionRepository,
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
    void nonMember_allPostsVisible_previewImagesFull_restBlurred() {
        ExamSubjectEntity subject = subject(1);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questionsWithImages(3, 2));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertTrue(res.locked());
        assertEquals(3, res.totalCount());
        assertEquals(3, res.questions().size());
        for (PublicFeQuestionResponse q : res.questions()) {
            assertEquals(2, q.totalImageCount());
            assertEquals("full", q.images().get(0).type());
            assertEquals("blur", q.images().get(1).type());
        }
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
    void listPeItems_requiresMembership() {
        ExamSubjectEntity subject = subject(3);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        org.mockito.Mockito.doThrow(new ForbiddenException("NO_ACTIVE_MEMBERSHIP", "no"))
                .when(accessGuard).requireActiveMembership(userId);

        assertThrows(ForbiddenException.class, () -> service.listPeItems("MLN111", userId));
    }

    private ExamSubjectEntity subject(int previewImageCount) {
        return ExamSubjectEntity.create(
                subjectId, "MLN111", "Title", null, null, null, null,
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
