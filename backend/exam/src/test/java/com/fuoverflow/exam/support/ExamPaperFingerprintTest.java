package com.fuoverflow.exam.support;

import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.domain.IngestAsset;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.domain.IngestQuestion;
import com.fuoverflow.exam.domain.IngestResource;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

class ExamPaperFingerprintTest {

    @Test
    void isStableWhenQuestionOrderChanges() {
        IngestQuestion a = question("111", List.of(20L, 10L));
        IngestQuestion b = question("222", List.of(30L, 40L));

        assertEquals(
                ExamPaperFingerprint.of(fePaper(List.of(a, b))),
                ExamPaperFingerprint.of(fePaper(List.of(b, a))));
    }

    @Test
    void isStableWhenAnswerOptionOrderChanges() {
        assertEquals(
                ExamPaperFingerprint.of(fePaper(List.of(question("111", List.of(10L, 20L))))),
                ExamPaperFingerprint.of(fePaper(List.of(question("111", List.of(20L, 10L))))));
    }

    @Test
    void fallsBackToImageHashesWhenAnswerOptionIdsAreAbsent() {
        String withImages = ExamPaperFingerprint.of(fePaper(List.of(question("111", List.of()))));

        assertEquals(64, withImages.length());
        assertNotEquals(
                withImages,
                ExamPaperFingerprint.of(fePaper(List.of(question("111", List.of(10L))))));
    }

    @Test
    void differsWhenExamCodeDiffers() {
        IngestPaper first = fePaper(List.of(question("111", List.of(10L))));
        IngestPaper second = new IngestPaper(
                "SCM302_SU26_FE_999999", first.paperType(), first.subjectCode(), first.term(),
                null, first.title(), null, 60, new BigDecimal("50.00"), 1,
                "eos-crawler", "999999", first.questions(), List.of(), List.of());

        assertNotEquals(ExamPaperFingerprint.of(first), ExamPaperFingerprint.of(second));
    }

    @Test
    void peFingerprintUsesImageAndResourceHashes() {
        IngestPaper paper = new IngestPaper(
                "PRJ301_SU26_PE_1", ExamPaperType.PE, "PRJ301", "SU26", null,
                "PE 1", null, null, null, null, "eos-crawler", "1",
                List.of(),
                List.of(new IngestAsset(0, "image/png", 10, "aa".repeat(32), new byte[]{1})),
                List.of(new IngestResource(0, null, "a.zip", "application/zip", 20,
                        "bb".repeat(32), "https://cdn.example.com/a.zip")));

        assertEquals(64, ExamPaperFingerprint.of(paper).length());
    }

    private static IngestPaper fePaper(List<IngestQuestion> questions) {
        return new IngestPaper(
                "SCM302_SU26_FE_553972", ExamPaperType.FE, "SCM302", "SU26", null,
                "SCM302 FE", null, 60, new BigDecimal("50.00"), questions.size(),
                "eos-crawler", "553972", questions, List.of(), List.of());
    }

    private static IngestQuestion question(String externalId, List<Long> optionIds) {
        return new IngestQuestion(
                externalId, 1, null, 1, 11001, BigDecimal.ONE,
                List.of(new IngestAsset(0, "image/png", 6853,
                        Sha256.hexUtf8("image-" + externalId), new byte[]{1, 2, 3})),
                optionIds);
    }
}
