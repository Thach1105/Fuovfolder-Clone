package com.fuoverflow.grading.persistence;

import org.junit.jupiter.api.Test;

import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class GradingPaperAnswerEntityTest {

    @Test
    void optionStartsIncorrect() {
        GradingPaperAnswerEntity option = GradingPaperAnswerEntity.of(
                UUID.randomUUID(), UUID.randomUUID(), 111L, 9001L, 0, "A", null);

        assertFalse(option.isCorrect());
        assertEquals(0, option.getOptionIndex());
        assertEquals(9001L, option.getQaid());
    }

    @Test
    void setCorrectFlipsTheFlagBothWays() {
        GradingPaperAnswerEntity option = GradingPaperAnswerEntity.of(
                UUID.randomUUID(), UUID.randomUUID(), 111L, 9001L, 0, "A", null);

        option.setCorrect(true);
        assertTrue(option.isCorrect());

        option.setCorrect(false);
        assertFalse(option.isCorrect());
    }
}
