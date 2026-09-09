package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import com.fuoverflow.exam.api.dto.webhook.QuestionPayload;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.domain.IngestPaper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EosPayloadAdapterTest {

    private static final String PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final EosPayloadAdapter adapter = new EosPayloadAdapter();

    @Test
    void mapsAGrammarSectionPaper() throws Exception {
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"SCM302_SU26_FE_553972","Duration":60,"Mark":50.0,"NoOfQuestion":2,
                 "QD":{"MultipleChoices":2},
                 "GrammarQuestions":[
                   {"QID":1920809737,"CourseId":"SCM302","ChapterId":11001,"PID":-1,
                    "Text":"(Choose 1 answer) \\r\\n\\r\\n","Mark":1.0,"QType":1,
                    "QuestionAnswers":[{"QAID":11,"QID":1920809737,"Text":""},
                                       {"QAID":22,"QID":1920809737,"Text":""}],
                    "ImageData":"%s","ImageSize":6853},
                   {"QID":1920809738,"ChapterId":11002,"Text":"(Choose 2 answers)","Mark":1.0,"QType":1,
                    "QuestionAnswers":[{"QAID":33,"QID":1920809738,"Text":""}],
                    "ImageData":"%s","ImageSize":7000}]}
                """.formatted(PNG, PNG));

        assertEquals("SCM302_SU26_FE_553972", request.paper().examCode());
        assertEquals("FE", request.paper().paperType());
        assertEquals("SCM302", request.paper().subjectCode());
        assertEquals("SU26", request.paper().term());
        assertEquals(60, request.paper().durationMinutes());
        assertEquals(2, request.paper().declaredQuestionCount());
        assertEquals("553972", request.paper().source().externalPaperId());
        assertEquals(2, request.paper().questions().size());

        QuestionPayload first = request.paper().questions().get(0);
        assertEquals("1920809737", first.externalId());
        assertEquals(1, first.displayNo());
        assertEquals(11001, first.chapterId());
        assertEquals(PNG, first.images().get(0).contentBase64());
        assertEquals(6853L, first.images().get(0).sizeBytes());
        assertEquals(List.of(11L, 22L), first.answerOptionIds());
        assertEquals(2, request.paper().questions().get(1).displayNo());
    }

    @Test
    void derivesAStableEventIdFromCodeAndContent() throws Exception {
        String body = onePaper("SCM302_SU26_FE_553972");

        String first = adapt(body).eventId();
        String second = adapt(body).eventId();
        String other = adapt(onePaper("SCM302_SU26_FE_999999")).eventId();

        assertEquals(first, second, "cùng nội dung phải cho cùng eventId");
        assertTrue(first.startsWith("eos:SCM302_SU26_FE_553972:"), first);
        assertTrue(first.length() <= 120, "event_id là varchar(120)");
        assertTrue(!first.equals(other));
    }

    @Test
    void keepsEventIdWithinTheColumnWidthForAbsurdlyLongCodes() throws Exception {
        String longCode = "X".repeat(200);
        String eventId = adapt(onePaper(longCode)).eventId();

        assertTrue(eventId.length() <= 120, "dài " + eventId.length());
    }

    @Test
    void gathersEveryQuestionBearingSection() throws Exception {
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"ENG_SU26_FE_1",
                 "GrammarQuestions":[{"QID":1,"Text":"a","ImageData":"%s"}],
                 "FillBlankQuestions":[{"QID":2,"Text":"b","ImageData":"%s"}],
                 "IndicateMQuestions":[{"QID":3,"Text":"c","ImageData":"%s"}],
                 "ReadingQuestions":[{"PassageQuestions":[{"QID":4,"Text":"d","ImageData":"%s"}]}]}
                """.formatted(PNG, PNG, PNG, PNG));

        assertEquals(List.of("1", "2", "3", "4"),
                request.paper().questions().stream().map(QuestionPayload::externalId).toList());
    }

    @Test
    void skipsQuestionsWithoutAnImage() throws Exception {
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"ENG_SU26_FE_1",
                 "GrammarQuestions":[
                   {"QID":1,"Text":"co anh","ImageData":"%s"},
                   {"QID":2,"Text":"(Choose 1 answer)","ImageData":""},
                   {"QID":3,"Text":"khong co anh"}]}
                """.formatted(PNG));

        assertEquals(1, request.paper().questions().size());
        assertEquals("1", request.paper().questions().get(0).externalId());
    }

    @Test
    void rejectsAPayloadWhereNoQuestionCarriesAnImage() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> adapt("""
                {"ExamCode":"ENG_SU26_FE_1",
                 "GrammarQuestions":[{"QID":1,"Text":"(Choose 1 answer)"}]}
                """));

        assertEquals("WEBHOOK_EOS_NO_IMAGE_QUESTIONS", ex.code());
    }

    @Test
    void rejectsAPayloadWithoutAnExamCode() {
        BadRequestException ex = assertThrows(BadRequestException.class, () -> adapt("""
                {"GrammarQuestions":[{"QID":1,"ImageData":"x"}]}
                """));

        assertEquals("WEBHOOK_EOS_EXAM_CODE_REQUIRED", ex.code());
    }

    @Test
    void treatsANonStandardExamCodeAsFeWithoutATerm() throws Exception {
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"TEST_EOS_Client_278333","Duration":20,"Mark":28.5,
                 "GrammarQuestions":[{"QID":9,"Text":"x","ImageData":"%s"}]}
                """.formatted(PNG));

        assertEquals("FE", request.paper().paperType());
        assertEquals("TEST", request.paper().subjectCode());
        assertNull(request.paper().term(), "mã không theo quy ước thì không suy ra kỳ");
    }

    @Test
    void mapsAProgressTestCodeToFe() throws Exception {
        // PT/MID vẫn là bộ câu hỏi trắc nghiệm; bảng chỉ lưu FE hoặc PE.
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"MAE101_SU26_PT_42",
                 "GrammarQuestions":[{"QID":1,"Text":"x","ImageData":"%s"}]}
                """.formatted(PNG));

        assertEquals("FE", request.paper().paperType());
        assertEquals("SU26", request.paper().term());
    }

    @Test
    void usesMidWhenAQuestionHasNoQid() throws Exception {
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"ENG_SU26_FE_1",
                 "GrammarQuestions":[{"MID":777,"Text":"x","ImageData":"%s"}]}
                """.formatted(PNG));

        assertEquals("777", request.paper().questions().get(0).externalId());
    }

    @Test
    void parsesTheNewDashShapeAndCapturesCampus() throws Exception {
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"SDN302-PE-SU26-HCM",
                 "GrammarQuestions":[{"QID":1,"Text":"x","ImageData":"%s"}]}
                """.formatted(PNG));

        assertEquals("SDN302", request.paper().subjectCode());
        // The exam code carries a PE type segment, but this adapter only ever produces
        // question-based content, so it must still land as FE — anything else is rejected
        // downstream as an empty PE paper (no images/resources).
        assertEquals("FE", request.paper().paperType());
        assertEquals("SU26", request.paper().term());
        assertEquals("HCM", request.paper().campus());
    }

    @Test
    void aPeLabeledDashCodeStillValidatesSuccessfullyAsFe() throws Exception {
        PaperWebhookRequest request = adapt("""
                {"ExamCode":"SDN302-PE-SU26-HCM",
                 "GrammarQuestions":[{"QID":1,"Text":"x","ImageData":"%s"}]}
                """.formatted(PNG));

        IngestPaper ingested = new ExamWebhookPayloadValidator(5_242_880L, 200).validate(request);

        assertEquals(ExamPaperType.FE, ingested.paperType());
        assertEquals("HCM", ingested.campus());
    }

    @Test
    void oldUnderscoreShapeStillHasNoCampus() throws Exception {
        PaperWebhookRequest request = adapt(onePaper("SCM302_SU26_FE_553972"));

        assertNull(request.paper().campus());
    }

    private PaperWebhookRequest adapt(String json) throws Exception {
        return adapter.adapt(objectMapper.readTree(json), json);
    }

    private static String onePaper(String examCode) {
        return """
                {"ExamCode":"%s","GrammarQuestions":[{"QID":1,"Text":"x","ImageData":"%s"}]}
                """.formatted(examCode, PNG);
    }
}
