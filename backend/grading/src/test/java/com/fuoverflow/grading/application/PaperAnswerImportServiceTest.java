package com.fuoverflow.grading.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.grading.api.dto.AnswerItem;
import com.fuoverflow.grading.api.dto.ApplyAnswersRequest;
import com.fuoverflow.grading.api.dto.ApplyAnswersResponse;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperSection;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import com.fuoverflow.grading.support.Sha256;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperAnswerImportServiceTest {

    @Mock private GradingPaperRepository paperRepository;
    @Mock private GradingPaperQuestionRepository questionRepository;
    @Mock private GradingPaperAnswerRepository answerRepository;

    private PaperAnswerImportService service;
    private GradingPaperEntity paper;

    @BeforeEach
    void setUp() {
        service = new PaperAnswerImportService(paperRepository, questionRepository, answerRepository);
        paper = GradingPaperEntity.draft("CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 1, 60,
                new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
    }

    /** Image-only question: no text, has an image, so its options live inside the picture. */
    private GradingPaperQuestionEntity imageQuestion(long qid, int expected) {
        return GradingPaperQuestionEntity.of(paper.getId(), qid, PaperSection.GRAMMAR, 1, 1,
                BigDecimal.ONE, null, null, "i".repeat(64), "c".repeat(64),
                expected > 1 ? AnswerMode.MULTI : AnswerMode.SINGLE, expected);
    }

    private GradingPaperQuestionEntity textQuestion(long qid) {
        return GradingPaperQuestionEntity.of(paper.getId(), qid, PaperSection.GRAMMAR, 1, 1,
                BigDecimal.ONE, null, "Cau hoi text?", null, "c".repeat(64), AnswerMode.SINGLE, 1);
    }

    private List<GradingPaperAnswerEntity> options(GradingPaperQuestionEntity q, String... texts) {
        List<GradingPaperAnswerEntity> list = new ArrayList<>();
        for (int i = 0; i < texts.length; i++) {
            list.add(GradingPaperAnswerEntity.of(paper.getId(), q.getId(), q.getQid(), 9000L + i, i,
                    texts[i], texts[i] == null ? null : Sha256.hexUtf8(texts[i])));
        }
        return list;
    }

    private ApplyAnswersRequest request(AnswerItem... items) {
        return new ApplyAnswersRequest("CSP201m_SU26_FE_315379", "dump-2026-08", "KEEP_EXISTING",
                false, List.of(items));
    }

    private ApplyAnswersRequest dryRun(AnswerItem... items) {
        return new ApplyAnswersRequest("CSP201m_SU26_FE_315379", "dump-2026-08", "KEEP_EXISTING",
                true, List.of(items));
    }

    private void stubPaper() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
    }

    private void stubQuestion(GradingPaperQuestionEntity q, List<GradingPaperAnswerEntity> opts) {
        when(questionRepository.findByPaperIdAndQid(paper.getId(), q.getQid())).thenReturn(Optional.of(q));
        if (opts != null) {
            when(answerRepository.findByPaperIdAndQid(paper.getId(), q.getQid())).thenReturn(opts);
        }
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));
    }

    @Test
    void byQaidIsTrustedAndCountsAsAnswered() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        List<GradingPaperAnswerEntity> opts = options(q, null, null);
        stubPaper();
        stubQuestion(q, opts);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, List.of(9001L), null, null)));

        assertEquals(1, response.applied());
        assertEquals(0, response.suggested());
        assertTrue(opts.get(1).isCorrect());
        assertEquals(AnswerSource.IMPORTED, q.getAnswerSource());
        assertTrue(q.isAnswered());
    }

    @Test
    void byLetterOnImageQuestionIsOnlyASuggestion() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        List<GradingPaperAnswerEntity> opts = options(q, null, null, null, null);
        stubPaper();
        stubQuestion(q, opts);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, null, List.of("C"))));

        assertEquals(0, response.applied());
        assertEquals(1, response.suggested());
        assertTrue(opts.get(2).isCorrect(), "C maps to option_index 2");
        assertEquals(AnswerSource.SUGGESTED, q.getAnswerSource());
        assertFalse(q.isAnswered(), "a suggestion must not open the publish gate");
        assertEquals(List.of(111L), response.stillUnansweredQids());
    }

    @Test
    void byLetterOnTextQuestionIsRejected() {
        GradingPaperQuestionEntity q = textQuestion(111);
        stubPaper();
        stubQuestion(q, null);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, null, List.of("C"))));

        assertEquals(0, response.applied());
        assertEquals(1, response.skipped());
        assertEquals("LETTER_NOT_ALLOWED_FOR_TEXT_QUESTION", response.outcomes().get(0).reason());
    }

    @Test
    void byLetterBeyondOptionCountIsSkipped() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        stubPaper();
        stubQuestion(q, options(q, null, null));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, null, List.of("D"))));

        assertEquals(1, response.skipped());
        assertEquals("LETTER_OUT_OF_RANGE", response.outcomes().get(0).reason());
    }

    @Test
    void letterCountMustMatchExpectedAnswerCount() {
        GradingPaperQuestionEntity q = imageQuestion(111, 2);
        stubPaper();
        // the count check runs before options are loaded, so none are stubbed here
        stubQuestion(q, null);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, null, List.of("A"))));

        assertEquals(1, response.skipped());
        assertEquals("ANSWER_COUNT_MISMATCH", response.outcomes().get(0).reason());
    }

    @Test
    void byOptionTextMatchesOnHashAndIsTrusted() {
        GradingPaperQuestionEntity q = textQuestion(111);
        List<GradingPaperAnswerEntity> opts = options(q, "Sai", "Dung");
        stubPaper();
        stubQuestion(q, opts);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, List.of("Dung"), null)));

        assertEquals(1, response.applied());
        assertTrue(opts.get(1).isCorrect());
        assertEquals(AnswerSource.IMPORTED, q.getAnswerSource());
    }

    @Test
    void byOptionTextThatDoesNotExistIsSkipped() {
        GradingPaperQuestionEntity q = textQuestion(111);
        stubPaper();
        stubQuestion(q, options(q, "Sai", "Dung"));

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, null, List.of("Khong co"), null)));

        assertEquals(1, response.skipped());
        assertEquals("OPTION_TEXT_NOT_FOUND", response.outcomes().get(0).reason());
    }

    @Test
    void keepExistingDoesNotOverwriteManualAnswer() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        q.applyAnswer(AnswerSource.MANUAL, null);
        stubPaper();
        stubQuestion(q, null);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, List.of(9001L), null, null)));

        assertEquals(1, response.skipped());
        assertEquals("ALREADY_ANSWERED_MANUALLY", response.outcomes().get(0).reason());
        assertEquals(AnswerSource.MANUAL, q.getAnswerSource());
    }

    @Test
    void overwritePolicyReplacesAManualAnswer() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        q.applyAnswer(AnswerSource.MANUAL, null);
        List<GradingPaperAnswerEntity> opts = options(q, null, null);
        stubPaper();
        stubQuestion(q, opts);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                new ApplyAnswersRequest("CSP201m_SU26_FE_315379", "dump", "OVERWRITE", false,
                        List.of(new AnswerItem(111L, List.of(9001L), null, null))));

        assertEquals(1, response.applied());
        assertEquals(AnswerSource.IMPORTED, q.getAnswerSource());
    }

    @Test
    void failPolicyThrowsOnConflict() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        q.applyAnswer(AnswerSource.MANUAL, null);
        stubPaper();
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.apply(paper.getId(),
                new ApplyAnswersRequest("CSP201m_SU26_FE_315379", "dump", "FAIL", false,
                        List.of(new AnswerItem(111L, List.of(9001L), null, null)))));

        assertEquals("ANSWER_CONFLICT", ex.code());
    }

    @Test
    void ambiguousKeyIsSkipped() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        stubPaper();
        stubQuestion(q, null);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(111L, List.of(9001L), null, List.of("A"))));

        assertEquals(1, response.skipped());
        assertEquals("AMBIGUOUS_KEY", response.outcomes().get(0).reason());
    }

    @Test
    void unknownQidIsSkippedNotFatal() {
        stubPaper();
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 999L)).thenReturn(Optional.empty());
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of());

        ApplyAnswersResponse response = service.apply(paper.getId(),
                request(new AnswerItem(999L, List.of(1L), null, null)));

        assertEquals(1, response.skipped());
        assertEquals("QID_NOT_IN_PAPER", response.outcomes().get(0).reason());
    }

    @Test
    void dryRunComputesOutcomesWithoutWritingAnything() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        List<GradingPaperAnswerEntity> opts = options(q, null, null);
        stubPaper();
        stubQuestion(q, opts);

        ApplyAnswersResponse response = service.apply(paper.getId(),
                dryRun(new AnswerItem(111L, List.of(9001L), null, null)));

        assertEquals(1, response.applied(), "dry run still reports what would apply");
        assertFalse(opts.get(1).isCorrect(), "dry run must not mutate an entity");
        assertFalse(q.isAnswered(), "dry run must not mutate an entity");
        assertNull(q.getAnswerSource());
        verify(answerRepository, never()).saveAll(any());
        verify(questionRepository, never()).save(any());
    }

    @Test
    void examCodeMismatchIsRejectedOutright() {
        stubPaper();

        ApplyAnswersRequest wrong = new ApplyAnswersRequest("SCM302_SU26_FE_553972", "dump",
                "KEEP_EXISTING", false, List.of(new AnswerItem(111L, List.of(9001L), null, null)));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.apply(paper.getId(), wrong));

        assertEquals("EXAM_CODE_MISMATCH", ex.code());
    }

    @Test
    void publishedPaperCannotReceiveAnswers() {
        paper.markReady();
        stubPaper();

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.apply(paper.getId(), request(new AnswerItem(111L, List.of(9001L), null, null))));

        assertEquals("PAPER_ALREADY_PUBLISHED", ex.code());
    }

    @Test
    void confirmSuggestionPromotesItToManual() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        q.suggestAnswer("dump-2026-08");
        stubPaper();
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));

        service.confirmSuggestion(paper.getId(), 111L);

        assertEquals(AnswerSource.MANUAL, q.getAnswerSource());
        assertTrue(q.isAnswered());
        assertEquals("dump-2026-08", q.getAnswerSourceRef(), "provenance must survive confirmation");
    }

    @Test
    void confirmRejectsQuestionWithoutSuggestion() {
        GradingPaperQuestionEntity q = imageQuestion(111, 1);
        stubPaper();
        when(questionRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(Optional.of(q));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.confirmSuggestion(paper.getId(), 111L));

        assertEquals("NO_SUGGESTION_TO_CONFIRM", ex.code());
    }
}
