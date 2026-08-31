package com.fuoverflow.grading.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.NormalizedPaper;
import com.fuoverflow.grading.domain.NormalizedQuestion;
import com.fuoverflow.grading.domain.PaperSection;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PaperPayloadNormalizerTest {

    private final ObjectMapper mapper = new ObjectMapper();
    private final PaperPayloadNormalizer normalizer = new PaperPayloadNormalizer();

    private JsonNode json(String raw) {
        try {
            return mapper.readTree(raw);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    /** CSP201m shape: GrammarQuestions only, content lives in ImageData, option Text blank. */
    private static final String IMAGE_ONLY = """
        {"ExamCode":"CSP201m_SU26_FE_315379","Duration":60,"Mark":50.0,"NoOfQuestion":2,
         "QD":{"MultipleChoices":2},
         "GrammarQuestions":[
           {"QID":111,"PID":-1,"QType":1,"Mark":1.0,"ChapterId":5963,"Text":"(Choose 1 answer) ",
            "ImageData":"aGVsbG8=","ImageSize":5,"QuestionLOs":[],
            "QuestionAnswers":[{"QID":111,"QAID":9001,"Text":""},{"QID":111,"QAID":9002,"Text":""}]},
           {"QID":222,"PID":-1,"QType":1,"Mark":1.0,"ChapterId":5964,"Text":"(Choose 2 answers)",
            "ImageData":"d29ybGQ=","ImageSize":5,"QuestionLOs":[],
            "QuestionAnswers":[{"QID":222,"QAID":9003,"Text":""},{"QID":222,"QAID":9004,"Text":""},
                               {"QID":222,"QAID":9005,"Text":""}]}],
         "ReadingQuestions":[],"MatchQuestions":[],"FillBlankQuestions":[],"IndicateMQuestions":[]}
        """;

    /** TEST_EOS shape: every section, option Text populated, QD.Reading counts PASSAGES not questions. */
    private static final String MULTI_SECTION = """
        {"ExamCode":"TEST_EOS_Client_278333","Duration":20,"Mark":28.5,"NoOfQuestion":4,
         "TestType":1,"QD":{"Reading":1,"Matching":1,"FillBlank":1,"MultipleChoices":1},
         "GrammarQuestions":[
           {"QID":15000,"PID":-1,"QType":1,"Mark":1.0,"ChapterId":689,"Text":"(Choose 1 answer) Cau A?",
            "ImageData":"","QuestionLOs":[],
            "QuestionAnswers":[{"QID":15000,"QAID":64851,"Text":"Dung"},
                               {"QID":15000,"QAID":64852,"Text":"Sai"}]}],
         "ReadingQuestions":[
           {"PID":6,"ChapterId":690,"CourseId":"TEST_EOS","Text":"Doan van",
            "PassageQuestions":[
              {"PID":6,"QID":15010,"Mark":0.5,"ChapterId":-1,"Text":"Cau doc?","ImageData":"",
               "QuestionLOs":[],
               "QuestionAnswers":[{"QID":15010,"QAID":64883,"Text":"X"},
                                  {"QID":15010,"QAID":64884,"Text":"Y"}]}]}],
         "MatchQuestions":[
           {"MID":5,"Mark":10.0,"ChapterId":692,"CourseId":"TEST_EOS",
            "ColumnA":"1. A","ColumnB":"A. B","Solution":"#;#","QuestionLOs":[]}],
         "FillBlankQuestions":[
           {"QID":15006,"PID":-1,"QType":6,"Lock":true,"Mark":4.0,"ChapterId":691,
            "Text":"Dien (###) va (###)","ImageData":"","QuestionLOs":[],
            "QuestionAnswers":[{"QID":15006,"QAID":64870,"Text":""},
                               {"QID":15006,"QAID":64871,"Text":""}]}],
         "IndicateMQuestions":[]}
        """;

    @Test
    void normalizesImageOnlyPaper() {
        NormalizedPaper paper = normalizer.normalize(json(IMAGE_ONLY));

        assertEquals("CSP201m_SU26_FE_315379", paper.examCode());
        assertEquals("CSP201m", paper.subjectCode());
        assertEquals(60, paper.durationMinutes());
        assertEquals(2, paper.questions().size());

        NormalizedQuestion first = paper.questions().get(0);
        assertEquals(111L, first.qid());
        assertEquals(PaperSection.GRAMMAR, first.section());
        assertEquals(1, first.displayNo());
        assertEquals("aGVsbG8=", first.imageBase64());
        assertNull(first.questionText(), "boilerplate (Choose N answers) is not question content");
        assertEquals(2, first.options().size());
        assertEquals(9001L, first.options().get(0).qaid());
        assertEquals(0, first.options().get(0).optionIndex());
    }

    @Test
    void derivesAnswerModeAndExpectedCountFromChooseMarker() {
        NormalizedPaper paper = normalizer.normalize(json(IMAGE_ONLY));

        assertEquals(AnswerMode.SINGLE, paper.questions().get(0).answerMode());
        assertEquals(1, paper.questions().get(0).expectedAnswerCount());

        assertEquals(AnswerMode.MULTI, paper.questions().get(1).answerMode());
        assertEquals(2, paper.questions().get(1).expectedAnswerCount());
    }

    @Test
    void normalizesEverySectionOfMultiSectionPaper() {
        NormalizedPaper paper = normalizer.normalize(json(MULTI_SECTION));

        assertEquals(4, paper.questions().size());
        assertTrue(paper.questions().stream().anyMatch(q -> q.section() == PaperSection.GRAMMAR));
        assertTrue(paper.questions().stream().anyMatch(q -> q.section() == PaperSection.READING));
        assertTrue(paper.questions().stream().anyMatch(q -> q.section() == PaperSection.FILL_BLANK));
        assertTrue(paper.questions().stream().anyMatch(q -> q.section() == PaperSection.MATCH));
    }

    @Test
    void passageQuestionHasNoQTypeAndIsNotRejected() {
        NormalizedPaper paper = normalizer.normalize(json(MULTI_SECTION));

        NormalizedQuestion reading = paper.questions().stream()
                .filter(q -> q.section() == PaperSection.READING).findFirst().orElseThrow();
        assertNull(reading.qType());
        assertEquals(15010L, reading.qid());
    }

    @Test
    void fillBlankAndMatchAreTextMode() {
        NormalizedPaper paper = normalizer.normalize(json(MULTI_SECTION));

        assertEquals(AnswerMode.TEXT, paper.questions().stream()
                .filter(q -> q.section() == PaperSection.FILL_BLANK).findFirst().orElseThrow().answerMode());
        assertEquals(AnswerMode.TEXT, paper.questions().stream()
                .filter(q -> q.section() == PaperSection.MATCH).findFirst().orElseThrow().answerMode());
    }

    @Test
    void matchQuestionUsesMidAsQidAndHasNoOptions() {
        NormalizedPaper paper = normalizer.normalize(json(MULTI_SECTION));

        NormalizedQuestion match = paper.questions().stream()
                .filter(q -> q.section() == PaperSection.MATCH).findFirst().orElseThrow();
        assertEquals(5L, match.qid());
        assertTrue(match.options().isEmpty());
    }

    @Test
    void keepsQuestionTextThatFollowsTheChooseMarker() {
        NormalizedPaper paper = normalizer.normalize(json(MULTI_SECTION));

        NormalizedQuestion grammar = paper.questions().stream()
                .filter(q -> q.section() == PaperSection.GRAMMAR).findFirst().orElseThrow();
        assertEquals("Cau A?", grammar.questionText());
        assertEquals(1, grammar.expectedAnswerCount());
    }

    @Test
    void rejectsUnknownQType() {
        String bad = IMAGE_ONLY.replace("\"QType\":1,\"Mark\":1.0,\"ChapterId\":5963",
                                        "\"QType\":99,\"Mark\":1.0,\"ChapterId\":5963");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_UNSUPPORTED_QTYPE", ex.code());
    }

    @Test
    void rejectsWhenQdSumDoesNotMatchNoOfQuestion() {
        String bad = IMAGE_ONLY.replace("\"NoOfQuestion\":2", "\"NoOfQuestion\":7");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_COUNT_MISMATCH", ex.code());
    }

    @Test
    void rejectsWhenSectionCountDoesNotMatchQd() {
        String bad = IMAGE_ONLY.replace("\"MultipleChoices\":2", "\"MultipleChoices\":5")
                               .replace("\"NoOfQuestion\":2", "\"NoOfQuestion\":5");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_SECTION_COUNT_MISMATCH", ex.code());
    }

    @Test
    void rejectsBlankExamCode() {
        String bad = IMAGE_ONLY.replace("\"ExamCode\":\"CSP201m_SU26_FE_315379\"", "\"ExamCode\":\"\"");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_EXAM_CODE_REQUIRED", ex.code());
    }

    @Test
    void rejectsDuplicateQid() {
        String bad = IMAGE_ONLY.replace("\"QID\":222", "\"QID\":111");

        BadRequestException ex = assertThrows(BadRequestException.class, () -> normalizer.normalize(json(bad)));
        assertEquals("PAYLOAD_DUPLICATE_QID", ex.code());
    }
}
