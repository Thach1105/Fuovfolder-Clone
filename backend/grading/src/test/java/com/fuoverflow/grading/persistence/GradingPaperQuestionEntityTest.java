package com.fuoverflow.grading.persistence;

import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.AnswerSource;
import com.fuoverflow.grading.domain.PaperSection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingPaperQuestionEntityTest {

    private GradingPaperQuestionEntity newQuestion() {
        return GradingPaperQuestionEntity.of(UUID.randomUUID(), 489616748L, PaperSection.GRAMMAR, 1, 1,
                BigDecimal.ONE, 5963, null, "i".repeat(64), "c".repeat(64), AnswerMode.SINGLE, 1);
    }

    @Test
    void newQuestionIsUnansweredWithNoSource() {
        GradingPaperQuestionEntity question = newQuestion();

        assertFalse(question.isAnswered());
        assertNull(question.getAnswerSource());
        assertNull(question.getAnswerSourceRef());
    }

    @Test
    void applyAnswerMarksAnsweredWithProvenance() {
        GradingPaperQuestionEntity question = newQuestion();

        question.applyAnswer(AnswerSource.IMPORTED, "dump-2026-08");

        assertTrue(question.isAnswered());
        assertEquals(AnswerSource.IMPORTED, question.getAnswerSource());
        assertEquals("dump-2026-08", question.getAnswerSourceRef());
    }

    @Test
    void suggestAnswerRecordsSourceButLeavesQuestionUnanswered() {
        GradingPaperQuestionEntity question = newQuestion();

        question.suggestAnswer("askforhelp-dump");

        assertFalse(question.isAnswered(), "goi y khong duoc mo cong phat hanh");
        assertEquals(AnswerSource.SUGGESTED, question.getAnswerSource());
        assertEquals("askforhelp-dump", question.getAnswerSourceRef());
    }

    @Test
    void applyAnswerRefusesSuggestedSource() {
        GradingPaperQuestionEntity question = newQuestion();

        assertThrows(IllegalArgumentException.class,
                () -> question.applyAnswer(AnswerSource.SUGGESTED, "x"));
    }
}
