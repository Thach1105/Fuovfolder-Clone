package com.fuoverflow.grading.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.grading.api.dto.PaperDetailResponse;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperSection;
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
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PaperQueryServiceTest {

    @Mock private GradingPaperRepository paperRepository;
    @Mock private GradingPaperQuestionRepository questionRepository;
    @Mock private GradingPaperAnswerRepository answerRepository;

    private PaperQueryService service;
    private GradingPaperEntity paper;

    private static final String RAW = """
        {"ExamCode":"CSP201m_SU26_FE_315379",
         "GrammarQuestions":[{"QID":111,"ImageData":"aGVsbG8="},{"QID":222,"ImageData":""}],
         "ReadingQuestions":[{"PID":6,"PassageQuestions":[{"QID":333,"ImageData":"d29ybGQ="}]}]}
        """;

    @BeforeEach
    void setUp() {
        service = new PaperQueryService(paperRepository, questionRepository, answerRepository,
                new ObjectMapper());
        paper = GradingPaperEntity.draft("CSP201m_SU26_FE_315379", "CSP201m", "a".repeat(64), 2, 60,
                new BigDecimal("50.00"), RAW, "b".repeat(64), null, UUID.randomUUID());
    }

    private GradingPaperQuestionEntity question(long qid, boolean answered) {
        GradingPaperQuestionEntity q = GradingPaperQuestionEntity.of(paper.getId(), qid,
                PaperSection.GRAMMAR, 1, (int) qid, BigDecimal.ONE, null, null, "i".repeat(64),
                "c".repeat(64), AnswerMode.SINGLE, 1);
        if (answered) {
            q.applyAnswer(AnswerSource.MANUAL, null);
        }
        return q;
    }

    @Test
    void detailListsUnansweredQids() {
        GradingPaperQuestionEntity done = question(111, true);
        GradingPaperQuestionEntity todo = question(222, false);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId()))
                .thenReturn(List.of(done, todo));
        when(answerRepository.findByPaperIdOrderByQidAscOptionIndexAsc(paper.getId()))
                .thenReturn(List.of(GradingPaperAnswerEntity.of(
                        paper.getId(), done.getId(), 111L, 9001L, 0, "A", null)));

        PaperDetailResponse detail = service.detail(paper.getId());

        assertEquals(List.of(222L), detail.unansweredQids());
        assertEquals(1, detail.paper().answeredCount());
        assertEquals(1, detail.paper().unansweredCount());
        assertEquals(2, detail.questions().size());
    }

    @Test
    void detailExposesIsCorrectAndAnswerSourceForAdmin() {
        GradingPaperAnswerEntity option =
                GradingPaperAnswerEntity.of(paper.getId(), UUID.randomUUID(), 111L, 9001L, 0, "A", null);
        option.setCorrect(true);
        GradingPaperQuestionEntity q = question(111, true);
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())).thenReturn(List.of(q));
        when(answerRepository.findByPaperIdOrderByQidAscOptionIndexAsc(paper.getId()))
                .thenReturn(List.of(option));

        PaperDetailResponse detail = service.detail(paper.getId());

        assertTrue(detail.questions().get(0).options().get(0).isCorrect());
        assertEquals("MANUAL", detail.questions().get(0).answerSource());
        assertTrue(detail.questions().get(0).hasImage());
    }

    @Test
    void questionImageDecodesBase64FromRawPayload() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        byte[] image = service.questionImage(paper.getId(), 111L);

        assertArrayEquals("hello".getBytes(StandardCharsets.UTF_8), image);
    }

    @Test
    void questionImageAlsoFindsPassageQuestions() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        byte[] image = service.questionImage(paper.getId(), 333L);

        assertArrayEquals("world".getBytes(StandardCharsets.UTF_8), image);
    }

    @Test
    void questionImageThrowsWhenQuestionHasNoImage() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.questionImage(paper.getId(), 222L));

        assertEquals("QUESTION_IMAGE_NOT_FOUND", ex.code());
    }

    @Test
    void questionImageThrowsForUnknownQid() {
        when(paperRepository.findByIdAndDeletedAtIsNull(paper.getId())).thenReturn(Optional.of(paper));

        NotFoundException ex = assertThrows(NotFoundException.class,
                () -> service.questionImage(paper.getId(), 999L));

        assertEquals("QUESTION_IMAGE_NOT_FOUND", ex.code());
    }

    @Test
    void readySubjectCodesOnlyIncludesPublishedPapers() {
        GradingPaperEntity ready = GradingPaperEntity.draft("SCM302_SU26_FE_1", "SCM302", "d".repeat(64),
                1, 60, BigDecimal.TEN, "{}", "e".repeat(64), null, UUID.randomUUID());
        ready.markReady();
        when(paperRepository.findByDeletedAtIsNullOrderByCreatedAtDesc())
                .thenReturn(List.of(paper, ready));

        assertEquals(List.of("SCM302"), service.readySubjectCodes());
    }

    @Test
    void listFiltersByStatus() {
        GradingPaperEntity ready = GradingPaperEntity.draft("SCM302_SU26_FE_1", "SCM302", "d".repeat(64),
                1, 60, BigDecimal.TEN, "{}", "e".repeat(64), null, UUID.randomUUID());
        ready.markReady();
        when(paperRepository.findByDeletedAtIsNullOrderByCreatedAtDesc())
                .thenReturn(List.of(paper, ready));
        when(questionRepository.findByPaperIdOrderByDisplayNoAsc(ready.getId())).thenReturn(List.of());

        assertEquals(1, service.list(null, "READY").size());
        assertEquals("SCM302", service.list(null, "READY").get(0).subjectCode());
    }
}
