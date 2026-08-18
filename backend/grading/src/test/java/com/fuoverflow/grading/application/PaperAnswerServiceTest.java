package com.fuoverflow.grading.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperSection;
import com.fuoverflow.grading.domain.PaperStatus;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
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
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperAnswerServiceTest {

    @Mock private GradingPaperRepository paperRepository;
    @Mock private GradingPaperQuestionRepository questionRepository;
    @Mock private GradingPaperAnswerRepository answerRepository;

    private PaperAnswerService service;
    private GradingPaperEntity paper;

    @BeforeEach
    void setUp() {
        service = new PaperAnswerService(paperRepository, questionRepository, answerRepository);
        paper = GradingPaperEntity.draft("CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 1, 60,
                new BigDecimal("50.00"), "{}", "b".repeat(64), null, UUID.randomUUID());
    }

    private GradingPaperQuestionEntity question(long qid, AnswerMode mode, Integer expected) {
        return GradingPaperQuestionEntity.of(paper.getId(), qid, PaperSection.GRAMMAR, 1, 1,
                BigDecimal.ONE, null, null, null, "c".repeat(64), mode, expected);
    }

    private List<GradingPaperAnswerEntity> options(GradingPaperQuestionEntity q, long... qaids) {
        List<GradingPaperAnswerEntity> list = new ArrayList<>();
        for (int i = 0; i < qaids.length; i++) {
            list.add(GradingPaperAnswerEntity.of(paper.getId(), q.getId(), q.getQid(), qaids[i], i, null, null));
        }
        return list;
    }

    private void stubPaperAndQuestion(GradingPaperQuestionEntity q) {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdAndQid(paper.getId(), q.getQid())).thenReturn(Optional.of(q));
    }

    @Test
    void setAnswerMarksChosenOptionCorrectAndQuestionAnswered() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        List<GradingPaperAnswerEntity> opts = options(q, 9001, 9002);
        stubPaperAndQuestion(q);
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(opts);

        service.setAnswer(paper.getId(), 111L, List.of(9002L));

        assertFalse(opts.get(0).isCorrect());
        assertTrue(opts.get(1).isCorrect());
        assertTrue(q.isAnswered());
        assertEquals(AnswerSource.MANUAL, q.getAnswerSource());
    }

    @Test
    void setAnswerClearsAPreviouslyCorrectOption() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        List<GradingPaperAnswerEntity> opts = options(q, 9001, 9002);
        opts.get(0).setCorrect(true);
        stubPaperAndQuestion(q);
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(opts);

        service.setAnswer(paper.getId(), 111L, List.of(9002L));

        assertFalse(opts.get(0).isCorrect(), "dap an cu phai bi bo");
        assertTrue(opts.get(1).isCorrect());
    }

    @Test
    void setAnswerRejectsQaidFromAnotherQuestion() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        stubPaperAndQuestion(q);
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 111L)).thenReturn(options(q, 9001, 9002));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.setAnswer(paper.getId(), 111L, List.of(7777L)));

        assertEquals("QAID_NOT_IN_QUESTION", ex.code());
    }

    @Test
    void setAnswerRejectsWrongNumberOfAnswers() {
        GradingPaperQuestionEntity q = question(222, AnswerMode.MULTI, 2);
        stubPaperAndQuestion(q);
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 222L)).thenReturn(options(q, 1, 2, 3));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.setAnswer(paper.getId(), 222L, List.of(1L, 2L, 3L)));

        assertEquals("ANSWER_COUNT_MISMATCH", ex.code());
    }

    @Test
    void setAnswerCountsDistinctQaidsOnly() {
        GradingPaperQuestionEntity q = question(222, AnswerMode.MULTI, 2);
        stubPaperAndQuestion(q);
        when(answerRepository.findByPaperIdAndQid(paper.getId(), 222L)).thenReturn(options(q, 1, 2, 3));

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.setAnswer(paper.getId(), 222L, List.of(1L, 1L)));

        assertEquals("ANSWER_COUNT_MISMATCH", ex.code(), "gui trung mot qaid khong duoc tinh la 2 dap an");
    }

    @Test
    void setAnswerRejectsEmptySelection() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        stubPaperAndQuestion(q);

        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> service.setAnswer(paper.getId(), 111L, List.of()));

        assertEquals("ANSWER_REQUIRED", ex.code());
    }

    @Test
    void setAnswerRejectsPublishedPaper() {
        paper.markReady();
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        ConflictException ex = assertThrows(ConflictException.class,
                () -> service.setAnswer(paper.getId(), 111L, List.of(9001L)));

        assertEquals("PAPER_ALREADY_PUBLISHED", ex.code());
    }

    @Test
    void publishRejectsWhenAQuestionHasNoAnswer() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.publish(paper.getId()));

        assertEquals("PAPER_INCOMPLETE", ex.code());
        assertTrue(ex.getMessage().contains("111"), "loi phai neu ro qid con thieu");
        assertEquals(PaperStatus.DRAFT, paper.getStatus());
    }

    @Test
    void publishRejectsAQuestionThatOnlyHasASuggestion() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        q.suggestAnswer("askforhelp-dump");
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        ConflictException ex = assertThrows(ConflictException.class, () -> service.publish(paper.getId()));

        assertEquals("PAPER_INCOMPLETE", ex.code());
        assertEquals(PaperStatus.DRAFT, paper.getStatus());
    }

    @Test
    void publishSucceedsWhenEveryQuestionAnswered() {
        GradingPaperQuestionEntity q = question(111, AnswerMode.SINGLE, 1);
        q.applyAnswer(AnswerSource.MANUAL, null);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));

        service.publish(paper.getId());

        assertEquals(PaperStatus.READY, paper.getStatus());
    }

    @Test
    void publishRejectsUnknownPaper() {
        UUID missing = UUID.randomUUID();
        when(paperRepository.findByIdAndDeletedAtIsNull(missing)).thenReturn(Optional.empty());

        assertThrows(NotFoundException.class, () -> service.publish(missing));
    }

    @Test
    void softDeleteStampsDeletedAt() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        service.softDelete(paper.getId());

        assertTrue(paper.getDeletedAt() != null);
    }
}
