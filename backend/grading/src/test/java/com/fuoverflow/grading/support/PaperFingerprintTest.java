package com.fuoverflow.grading.support;

import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.NormalizedOption;
import com.fuoverflow.grading.domain.NormalizedPaper;
import com.fuoverflow.grading.domain.NormalizedQuestion;
import com.fuoverflow.grading.domain.PaperSection;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class PaperFingerprintTest {

    private NormalizedQuestion question(long qid, long... qaids) {
        List<NormalizedOption> options = new ArrayList<>();
        for (int i = 0; i < qaids.length; i++) {
            options.add(new NormalizedOption(qaids[i], i, null));
        }
        return new NormalizedQuestion(qid, PaperSection.GRAMMAR, 1, 1, BigDecimal.ONE,
                null, null, null, AnswerMode.SINGLE, 1, options);
    }

    private NormalizedPaper paper(List<NormalizedQuestion> questions) {
        return new NormalizedPaper("SCM302_SU26_FE_553972", "SCM302", 60,
                new BigDecimal("50.00"), questions.size(), questions);
    }

    @Test
    void isStableRegardlessOfQuestionOrder() {
        List<NormalizedQuestion> a = List.of(question(1, 10, 11), question(2, 20, 21));
        List<NormalizedQuestion> b = new ArrayList<>(a);
        Collections.reverse(b);

        assertEquals(PaperFingerprint.of(paper(a)), PaperFingerprint.of(paper(b)));
    }

    @Test
    void isStableRegardlessOfOptionOrder() {
        String forward = PaperFingerprint.of(paper(List.of(question(1, 10, 11, 12))));
        String shuffled = PaperFingerprint.of(paper(List.of(question(1, 12, 10, 11))));

        assertEquals(forward, shuffled);
    }

    @Test
    void changesWhenAQaidChanges() {
        String original = PaperFingerprint.of(paper(List.of(question(1, 10, 11))));
        String altered = PaperFingerprint.of(paper(List.of(question(1, 10, 99))));

        assertNotEquals(original, altered);
    }

    @Test
    void changesWhenExamCodeChanges() {
        List<NormalizedQuestion> questions = List.of(question(1, 10, 11));
        String other = PaperFingerprint.of(new NormalizedPaper(
                "SCM302_SU26_FE_999999", "SCM302", 60, new BigDecimal("50.00"), 1, questions));

        assertNotEquals(PaperFingerprint.of(paper(questions)), other);
    }

    @Test
    void changesWhenAQuestionIsMissing() {
        String twoQuestions = PaperFingerprint.of(paper(List.of(question(1, 10, 11), question(2, 20, 21))));
        String oneQuestion = PaperFingerprint.of(paper(List.of(question(1, 10, 11))));

        assertNotEquals(twoQuestions, oneQuestion);
    }

    @Test
    void submissionFingerprintMatchesPaperFingerprint() {
        List<NormalizedQuestion> questions = List.of(question(1, 10, 11), question(2, 20, 21));
        Map<Long, Set<Long>> fromDat = new LinkedHashMap<>();
        fromDat.put(2L, Set.of(21L, 20L));
        fromDat.put(1L, Set.of(11L, 10L));

        assertEquals(PaperFingerprint.of(paper(questions)),
                PaperFingerprint.ofSubmission("SCM302_SU26_FE_553972", fromDat));
    }

    @Test
    void questionWithoutOptionsStillContributes() {
        NormalizedQuestion match = new NormalizedQuestion(5L, PaperSection.MATCH, null, 1,
                BigDecimal.TEN, null, null, null, AnswerMode.TEXT, null, List.of());

        String withMatch = PaperFingerprint.of(paper(List.of(question(1, 10, 11), match)));
        String withoutMatch = PaperFingerprint.of(paper(List.of(question(1, 10, 11))));

        assertNotEquals(withMatch, withoutMatch);
    }

    @Test
    void hexIsLowercaseSixtyFourChars() {
        String hex = Sha256.hexUtf8("abc");

        assertEquals(64, hex.length());
        assertEquals(hex.toLowerCase(), hex);
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", hex);
    }
}
