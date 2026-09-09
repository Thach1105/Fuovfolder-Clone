package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminSubjectResponse;
import com.fuoverflow.exam.api.dto.CreateSubjectRequest;
import com.fuoverflow.exam.api.dto.UpdateSubjectRequest;
import com.fuoverflow.exam.config.ExamProperties;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class ExamSubjectAdminServiceTest {

    @Mock private ExamSubjectRepository subjectRepository;
    @Mock private ExamFeQuestionRepository feQuestionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamPaperRepository paperRepository;
    @Mock private ExamMediaService mediaService;
    @Mock private ExamMediaUrlResolver urlResolver;
    @Mock private ExamProperties properties;

    private ExamSubjectAdminService service;

    @BeforeEach
    void setUp() {
        service = new ExamSubjectAdminService(
                subjectRepository, feQuestionRepository, peItemRepository, paperRepository,
                mediaService, urlResolver, properties);
        lenient().when(subjectRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(urlResolver.plain(any())).thenAnswer(inv -> inv.getArgument(0));
        lenient().when(properties.defaultFePreviewImageCountOrDefault()).thenReturn(3);
        lenient().when(feQuestionRepository.countBySubjectIdAndDeletedAtIsNull(any())).thenReturn(0L);
        lenient().when(peItemRepository.countBySubjectIdAndDeletedAtIsNull(any())).thenReturn(0L);
        lenient().when(paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(any(), any())).thenReturn(0L);
        lenient().when(paperRepository.findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(any()))
                .thenReturn(Optional.empty());
    }

    @Test
    void createStoresCurriculumTerm() {
        when(subjectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull("PRF192")).thenReturn(false);

        AdminSubjectResponse response = service.create(new CreateSubjectRequest(
                "prf192", "PRF192", null, null, null, null, 1, null, null, null));

        assertEquals(1, response.curriculumTerm());
    }

    @Test
    void createWithoutCurriculumTermLeavesItUnassigned() {
        when(subjectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull("PRF192")).thenReturn(false);

        AdminSubjectResponse response = service.create(new CreateSubjectRequest(
                "prf192", "PRF192", null, null, null, null, null, null, null, null));

        assertNull(response.curriculumTerm(), "chưa gán kỳ phải là null, không phải 0");
    }

    @Test
    void updateOverwritesCurriculumTermWhenProvided() {
        ExamSubjectEntity existing = ExamSubjectEntity.create(
                UUID.randomUUID(), "PRF192", "PRF192", null, null, null, null, null,
                3, true, 0, Instant.now());
        when(subjectRepository.findByIdAndDeletedAtIsNull(existing.getId())).thenReturn(Optional.of(existing));

        AdminSubjectResponse response = service.update(existing.getId(), new UpdateSubjectRequest(
                null, null, null, null, null, null, 2, null, null, null));

        assertEquals(2, response.curriculumTerm());
    }

    @Test
    void updateWithoutCurriculumTermKeepsThePreviousValue() {
        ExamSubjectEntity existing = ExamSubjectEntity.create(
                UUID.randomUUID(), "PRF192", "PRF192", null, null, null, null, 4,
                3, true, 0, Instant.now());
        when(subjectRepository.findByIdAndDeletedAtIsNull(existing.getId())).thenReturn(Optional.of(existing));

        AdminSubjectResponse response = service.update(existing.getId(), new UpdateSubjectRequest(
                null, "Đổi tên", null, null, null, null, null, null, null, null));

        assertEquals(4, response.curriculumTerm(), "không gửi curriculumTerm thì phải giữ nguyên giá trị cũ");
    }

    @Test
    void listAllReportsPaperCountsAndLatestPaperRegardlessOfStatus() {
        ExamSubjectEntity subject = ExamSubjectEntity.create(
                UUID.randomUUID(), "PRF192", "PRF192", null, null, null, null, 1,
                3, true, 0, Instant.now());
        when(subjectRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc())
                .thenReturn(List.of(subject));
        when(paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(subject.getId(), ExamPaperType.FE.dbValue()))
                .thenReturn(2L);
        when(paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(subject.getId(), ExamPaperType.PE.dbValue()))
                .thenReturn(1L);

        ExamPaperEntity latest = mockLatestPaper("PRF192_SU26_FE_1", Instant.parse("2026-09-01T00:00:00Z"));
        when(paperRepository.findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(subject.getId()))
                .thenReturn(Optional.of(latest));

        List<AdminSubjectResponse> result = service.listAll();

        assertEquals(1, result.size());
        AdminSubjectResponse response = result.get(0);
        assertEquals(2, response.fePaperCount());
        assertEquals(1, response.pePaperCountAllStatuses());
        assertEquals("PRF192_SU26_FE_1", response.latestPaper().examCode());
        assertEquals("draft", response.latestPaper().status());
        assertTrue(response.latestPaper().createdAt().equals(Instant.parse("2026-09-01T00:00:00Z")));
    }

    @Test
    void listAllReportsNullLatestPaperWhenSubjectHasNoPapers() {
        ExamSubjectEntity subject = ExamSubjectEntity.create(
                UUID.randomUUID(), "PRF192", "PRF192", null, null, null, null, null,
                3, true, 0, Instant.now());
        when(subjectRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc())
                .thenReturn(List.of(subject));

        List<AdminSubjectResponse> result = service.listAll();

        assertNull(result.get(0).latestPaper());
    }

    @Test
    void getThrowsNotFoundForUnknownId() {
        UUID id = UUID.randomUUID();
        when(subjectRepository.findByIdAndDeletedAtIsNull(id)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.get(id));
    }

    private static ExamPaperEntity mockLatestPaper(String examCode, Instant createdAt) {
        // ExamPaperEntity has no public setters for examCode/paperType/status/createdAt and no
        // public no-arg constructor usable from another package — draft(...) is the only way to
        // build one from here, and it always assigns status = ExamPaperStatus.DRAFT.dbValue(),
        // which matches the "draft" expectation in the test above.
        return ExamPaperEntity.draft(
                UUID.randomUUID(), UUID.randomUUID(), ExamPaperType.FE, examCode, "SU26",
                null, examCode + " title", null, 60, null, 50,
                UUID.randomUUID().toString().replace("-", "").repeat(2),
                "webhook:eos-crawler", "1", 0, null, createdAt);
    }
}
