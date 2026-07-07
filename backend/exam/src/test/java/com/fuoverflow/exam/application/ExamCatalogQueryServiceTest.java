package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.persistence.ExamFeOptionRepository;
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
    @Mock private ExamFeOptionRepository feOptionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamPeResourceRepository peResourceRepository;
    @Mock private ExamAccessGuard accessGuard;
    @Mock private ExamMediaUrlResolver urlResolver;

    private ExamCatalogQueryService service;
    private UUID userId;
    private UUID subjectId;

    @BeforeEach
    void setUp() {
        service = new ExamCatalogQueryService(
                subjectRepository, feQuestionRepository, feOptionRepository,
                peItemRepository, peResourceRepository, accessGuard, urlResolver, new ObjectMapper());
        userId = UUID.randomUUID();
        subjectId = UUID.randomUUID();
        lenient().when(feOptionRepository.findByQuestionIdOrderBySortOrderAsc(any())).thenReturn(List.of());
        lenient().when(urlResolver.signedAll(any())).thenReturn(List.of());
    }

    @Test
    void nonMember_getsOnlyPreviewSlice_locked() {
        ExamSubjectEntity subject = subject(3);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questions(10));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertTrue(res.locked());
        assertEquals(10, res.totalCount());
        assertEquals(3, res.previewCount());
        assertEquals(3, res.questions().size());
        assertTrue(res.questions().stream().allMatch(q -> q.preview()));
    }

    @Test
    void member_getsFullBank_unlocked() {
        ExamSubjectEntity subject = subject(3);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(true);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questions(10));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertFalse(res.locked());
        assertEquals(10, res.totalCount());
        assertEquals(10, res.questions().size());
        assertTrue(res.questions().stream().noneMatch(q -> q.preview()));
    }

    @Test
    void nonMember_previewLargerThanBank_returnsAll() {
        ExamSubjectEntity subject = subject(5);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        when(accessGuard.hasActiveMembership(userId)).thenReturn(false);
        when(feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId))
                .thenReturn(questions(2));

        PublicFeQuestionListResponse res = service.listFeQuestions("MLN111", userId);

        assertTrue(res.locked());
        assertEquals(2, res.questions().size());
    }

    @Test
    void listPeItems_requiresMembership() {
        ExamSubjectEntity subject = subject(3);
        when(subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull("MLN111")).thenReturn(Optional.of(subject));
        org.mockito.Mockito.doThrow(new ForbiddenException("NO_ACTIVE_MEMBERSHIP", "no"))
                .when(accessGuard).requireActiveMembership(userId);

        assertThrows(ForbiddenException.class, () -> service.listPeItems("MLN111", userId));
    }

    private ExamSubjectEntity subject(int previewCount) {
        return ExamSubjectEntity.create(
                subjectId, "MLN111", "Title", null, null, null, null,
                previewCount, true, 0, Instant.now());
    }

    private List<ExamFeQuestionEntity> questions(int n) {
        List<ExamFeQuestionEntity> list = new ArrayList<>();
        Instant now = Instant.now();
        for (int i = 0; i < n; i++) {
            list.add(ExamFeQuestionEntity.create(
                    UUID.randomUUID(), subjectId, "Q" + i, "[]", null, false, i, now));
        }
        return list;
    }
}
