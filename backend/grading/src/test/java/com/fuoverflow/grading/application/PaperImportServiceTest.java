package com.fuoverflow.grading.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.grading.api.dto.PaperPreviewResponse;
import com.fuoverflow.grading.api.dto.PaperSummaryResponse;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperSection;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import com.fuoverflow.grading.support.PaperFingerprint;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperImportServiceTest {

    @Mock private GradingPaperRepository paperRepository;
    @Mock private GradingPaperQuestionRepository questionRepository;
    @Mock private GradingPaperAnswerRepository answerRepository;

    private PaperImportService service;
    private final ObjectMapper mapper = new ObjectMapper();

    private static final String PAYLOAD = """
        {"ExamCode":"CSP201m_SU26_FE_315379","Duration":60,"Mark":50.0,"NoOfQuestion":1,
         "QD":{"MultipleChoices":1},
         "GrammarQuestions":[
           {"QID":111,"PID":-1,"QType":1,"Mark":1.0,"ChapterId":5963,"Text":"(Choose 1 answer)",
            "ImageData":"aGVsbG8=","ImageSize":5,"QuestionLOs":[],
            "QuestionAnswers":[{"QID":111,"QAID":9001,"Text":""},{"QID":111,"QAID":9002,"Text":""}]}],
         "ReadingQuestions":[],"MatchQuestions":[],"FillBlankQuestions":[],"IndicateMQuestions":[]}
        """;

    private JsonNode payload() {
        try {
            return mapper.readTree(PAYLOAD);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private String fingerprint() {
        return PaperFingerprint.of(new PaperPayloadNormalizer().normalize(payload()));
    }

    @BeforeEach
    void setUp() {
        service = new PaperImportService(paperRepository, questionRepository, answerRepository,
                new PaperPayloadNormalizer(), mapper);
    }

    @Test
    void previewParsesWithoutTouchingTheDatabase() {
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());

        PaperPreviewResponse preview = service.preview(payload());

        assertEquals("CSP201m_SU26_FE_315379", preview.examCode());
        assertEquals(1, preview.questionCount());
        assertEquals("NONE", preview.collision());
        assertEquals(64, preview.fingerprint().length());
        assertEquals(1, preview.questions().size());
        assertEquals("aGVsbG8=", preview.questions().get(0).imageBase64(),
                "UI needs the image to let the admin review before saving");
        assertEquals(2, preview.questions().get(0).options().size());

        verify(paperRepository, never()).save(any());
        verify(questionRepository, never()).saveAll(any());
        verify(answerRepository, never()).saveAll(any());
    }

    @Test
    void previewReportsCollisionWithExistingDraft() {
        GradingPaperEntity existing = draftPaper();
        GradingPaperQuestionEntity answered = questionOf(existing.getId());
        answered.applyAnswer(AnswerSource.MANUAL, null);
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existing));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(existing.getId())).thenReturn(List.of(answered));

        PaperPreviewResponse preview = service.preview(payload());

        assertEquals("EXISTING_DRAFT", preview.collision());
        assertEquals(existing.getId(), preview.collisionPaperId());
        assertEquals(1, preview.existingAnsweredCount(),
                "UI must say how many entered answers will be kept");
    }

    @Test
    void previewReportsCollisionWithPublishedPaper() {
        GradingPaperEntity published = draftPaper();
        published.markReady();
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(published));

        PaperPreviewResponse preview = service.preview(payload());

        assertEquals("EXISTING_PUBLISHED", preview.collision());
    }

    @Test
    void importCreatesDraftPaperWithQuestionsAndOptions() {
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(paperRepository.findByPayloadSha256AndDeletedAtIsNull(anyString())).thenReturn(Optional.empty());
        when(paperRepository.save(any())).thenAnswer(i -> i.getArgument(0));

        PaperSummaryResponse response =
                service.importPayload(UUID.randomUUID(), payload(), null, fingerprint());

        assertEquals("CSP201m_SU26_FE_315379", response.examCode());
        assertEquals("CSP201m", response.subjectCode());
        assertEquals("DRAFT", response.status());
        assertEquals(1, response.questionCount());
        assertEquals(0, response.answeredCount());
        assertEquals(1, response.unansweredCount());

        ArgumentCaptor<List<GradingPaperQuestionEntity>> questions = ArgumentCaptor.forClass(List.class);
        verify(questionRepository).saveAll(questions.capture());
        assertEquals(1, questions.getValue().size());
        assertEquals(111L, questions.getValue().get(0).getQid());

        ArgumentCaptor<List<GradingPaperAnswerEntity>> answers = ArgumentCaptor.forClass(List.class);
        verify(answerRepository).saveAll(answers.capture());
        assertEquals(2, answers.getValue().size());
        assertFalse(answers.getValue().get(0).isCorrect(), "answers must default to false");
    }

    @Test
    void importRejectsFingerprintThatDoesNotMatchThePayload() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.importPayload(UUID.randomUUID(), payload(), null, "f".repeat(64)));

        assertEquals("FINGERPRINT_MISMATCH", ex.code());
        verify(paperRepository, never()).save(any());
    }

    @Test
    void importRequiresConfirmedFingerprint() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.importPayload(UUID.randomUUID(), payload(), null, null));

        assertEquals("FINGERPRINT_REQUIRED", ex.code());
        verify(paperRepository, never()).save(any());
    }

    @Test
    void reimportOfDraftReturnsExistingPaperWithoutLosingAnswers() {
        GradingPaperEntity existing = draftPaper();
        GradingPaperQuestionEntity answered = questionOf(existing.getId());
        answered.applyAnswer(AnswerSource.MANUAL, null);
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(existing));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(existing.getId())).thenReturn(List.of(answered));

        PaperSummaryResponse response =
                service.importPayload(UUID.randomUUID(), payload(), null, fingerprint());

        assertEquals(existing.getId(), response.id());
        assertEquals("DRAFT", response.status());
        assertEquals(1, response.answeredCount(), "entered answers must survive a re-import");
        verify(paperRepository, never()).save(any());
        verify(answerRepository, never()).saveAll(any());
    }

    @Test
    void reimportOfPublishedPaperIsRejected() {
        GradingPaperEntity published = draftPaper();
        published.markReady();
        when(paperRepository.findByFingerprintAndDeletedAtIsNull(anyString())).thenReturn(Optional.of(published));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.importPayload(UUID.randomUUID(), payload(), null, fingerprint()));

        assertEquals("PAPER_ALREADY_PUBLISHED", ex.code());
        verify(paperRepository, never()).save(any());
    }

    @Test
    void importRequiresAPayload() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.importPayload(UUID.randomUUID(), null, null, fingerprint()));

        assertEquals("PAYLOAD_REQUIRED", ex.code());
    }

    private GradingPaperEntity draftPaper() {
        return GradingPaperEntity.draft("CSP201m_SU26_FE_315379", "CSP201m", fingerprint(), 1, 60,
                new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
    }

    private GradingPaperQuestionEntity questionOf(UUID paperId) {
        return GradingPaperQuestionEntity.of(paperId, 111L, PaperSection.GRAMMAR, 1, 1,
                BigDecimal.ONE, null, null, null, "c".repeat(64), AnswerMode.SINGLE, 1);
    }
}
