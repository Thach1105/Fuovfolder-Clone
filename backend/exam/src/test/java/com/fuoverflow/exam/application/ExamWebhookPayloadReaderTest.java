package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExamWebhookPayloadReaderTest {

    private static final String PNG =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC";

    private final ExamWebhookPayloadReader reader = new ExamWebhookPayloadReader(
            new ObjectMapper().registerModule(new JavaTimeModule()), new EosPayloadAdapter());

    @Test
    void readsACanonicalEnvelopeUnchanged() {
        PaperWebhookRequest request = reader.read("""
                {"eventId":"evt-1","eventType":"exam.paper.upserted",
                 "sentAt":"2026-09-01T03:54:59Z",
                 "paper":{"examCode":"SCM302_SU26_FE_1","paperType":"FE","subjectCode":"SCM302",
                 "title":"canonical","questions":[{"externalId":"1","questionText":"stem",
                 "images":[{"sortOrder":0,"mimeType":"image/png","contentBase64":"%s"}]}]}}
                """.formatted(PNG));

        assertEquals("evt-1", request.eventId());
        assertEquals("canonical", request.paper().title());
    }

    @Test
    void adaptsARawEosPayload() {
        PaperWebhookRequest request = reader.read("""
                {"ExamCode":"SCM302_SU26_FE_553972","Duration":60,"Mark":50.0,"NoOfQuestion":1,
                 "GrammarQuestions":[{"QID":1920809737,"Text":"(Choose 1 answer)","Mark":1.0,
                 "QuestionAnswers":[{"QAID":11}],"ImageData":"%s","ImageSize":6853}]}
                """.formatted(PNG));

        assertTrue(request.eventId().startsWith("eos:SCM302_SU26_FE_553972:"), request.eventId());
        assertEquals("SCM302", request.paper().subjectCode());
        assertEquals(1, request.paper().questions().size());
    }

    @Test
    void sameBodyReadTwiceYieldsTheSameEventId() {
        String body = """
                {"ExamCode":"ENG_SU26_FE_1",
                 "GrammarQuestions":[{"QID":1,"Text":"x","ImageData":"%s"}]}
                """.formatted(PNG);

        assertEquals(reader.read(body).eventId(), reader.read(body).eventId());
    }

    @Test
    void rejectsJsonThatIsNeitherShape() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reader.read("{\"hello\":\"world\"}"));

        assertEquals("WEBHOOK_PAYLOAD_UNRECOGNIZED", ex.code());
    }

    @Test
    void rejectsAnEosLookingPayloadWithNoQuestionArray() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reader.read("{\"ExamCode\":\"ENG_SU26_FE_1\",\"Duration\":60}"));

        assertEquals("WEBHOOK_PAYLOAD_UNRECOGNIZED", ex.code());
    }

    @Test
    void rejectsMalformedJson() {
        BadRequestException ex = assertThrows(BadRequestException.class,
                () -> reader.read("{\"ExamCode\":"));

        assertEquals("WEBHOOK_PAYLOAD_INVALID", ex.code());
    }

    @Test
    void rejectsAnEmptyBody() {
        assertEquals("WEBHOOK_PAYLOAD_INVALID",
                assertThrows(BadRequestException.class, () -> reader.read("")).code());
        assertEquals("WEBHOOK_PAYLOAD_INVALID",
                assertThrows(BadRequestException.class, () -> reader.read(null)).code());
    }

    @Test
    void canonicalShapeWinsWhenBothMarkersSomehowAppear() {
        // A canonical envelope that happens to carry EOS keys must not be re-adapted.
        PaperWebhookRequest request = reader.read("""
                {"eventId":"evt-9","ExamCode":"SHOULD_BE_IGNORED",
                 "GrammarQuestions":[{"QID":1,"ImageData":"%s"}],
                 "paper":{"examCode":"SCM302_SU26_FE_1","paperType":"FE","subjectCode":"SCM302",
                 "title":"canonical","questions":[{"externalId":"1","questionText":"stem",
                 "images":[{"sortOrder":0,"mimeType":"image/png","contentBase64":"%s"}]}]}}
                """.formatted(PNG, PNG));

        assertEquals("evt-9", request.eventId());
        assertEquals("SCM302_SU26_FE_1", request.paper().examCode());
    }
}
