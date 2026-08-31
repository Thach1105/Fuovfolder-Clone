package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.PayloadTooLargeException;
import com.fuoverflow.exam.api.dto.webhook.AssetPayload;
import com.fuoverflow.exam.api.dto.webhook.PaperPayload;
import com.fuoverflow.exam.api.dto.webhook.PaperSourcePayload;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import com.fuoverflow.exam.api.dto.webhook.QuestionPayload;
import com.fuoverflow.exam.api.dto.webhook.ResourcePayload;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.support.Sha256;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Base64;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ExamWebhookPayloadValidatorTest {

    /** Minimal 1x1 PNG: enough for a magic-byte check. */
    private static final String PNG_BASE64 =
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAIAAACQd1PeAAAADElEQVR4nGP4z8AAAAMBAQDJ/pLvAAAAAElFTkSuQmCC";
    private static final byte[] PNG = Base64.getDecoder().decode(PNG_BASE64);

    private final ExamWebhookPayloadValidator validator =
            new ExamWebhookPayloadValidator(5_242_880L, 200);

    @Test
    void acceptsFePaperAndStripsChooseMarker() {
        IngestPaper paper = validator.validate(feRequest(
                question("(Choose 2 answers) \r\n\r\nWhich two?", 2)));

        assertEquals(ExamPaperType.FE, paper.paperType());
        assertEquals("SCM302", paper.subjectCode());
        assertEquals("Which two?", paper.questions().get(0).questionText());
        assertEquals(2, paper.questions().get(0).expectedAnswerCount());
        assertEquals(1, paper.questions().get(0).displayNo());
        assertEquals("image/png", paper.questions().get(0).images().get(0).mimeType());
    }

    @Test
    void recomputesImageHashIgnoringTheDeclaredValue() {
        IngestPaper paper = validator.validate(feRequest(new QuestionPayload(
                "1", 1, "stem", 1, null, null,
                List.of(new AssetPayload(0, "image/png", 999L, "f".repeat(64), PNG_BASE64)),
                List.of())));

        assertEquals(Sha256.hex(PNG), paper.questions().get(0).images().get(0).sha256());
        assertEquals(PNG.length, paper.questions().get(0).images().get(0).sizeBytes());
    }

    @Test
    void keepsNullQuestionTextWhenOnlyMarkerPresent() {
        IngestPaper paper = validator.validate(feRequest(question("(Choose 1 answer) \r\n\r\n", 1)));

        assertNull(paper.questions().get(0).questionText());
        assertEquals(1, paper.questions().get(0).expectedAnswerCount());
    }

    @Test
    void rejectsFePaperWithoutQuestions() {
        PaperWebhookRequest request = request(payload("FE", List.of(), List.of(), List.of()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_FE_QUESTIONS_REQUIRED", ex.code());
    }

    @Test
    void rejectsPePaperWithNeitherImagesNorResources() {
        PaperWebhookRequest request = request(payload("PE", List.of(), List.of(), List.of()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_PE_CONTENT_REQUIRED", ex.code());
    }

    @Test
    void acceptsPePaperWithImagesAndResources() {
        PaperWebhookRequest request = request(new PaperPayload(
                "PRJ301_SU26_PE_1", "PE", "PRJ301", "SU26", null, "PE 1", "desc",
                null, null, null, source(), List.of(),
                List.of(new AssetPayload(0, "image/png", (long) PNG.length, null, PNG_BASE64)),
                List.of(new ResourcePayload(0, "Starter", "a.zip", "application/zip",
                        4823910L, "b".repeat(64), "https://cdn.example.com/a.zip"))));

        IngestPaper paper = validator.validate(request);

        assertEquals(ExamPaperType.PE, paper.paperType());
        assertEquals(1, paper.images().size());
        assertEquals(1, paper.resources().size());
        assertEquals("a.zip", paper.resources().get(0).filename());
    }

    @Test
    void rejectsQuestionsOnPePaper() {
        PaperWebhookRequest request = request(new PaperPayload(
                "PRJ301_SU26_PE_1", "PE", "PRJ301", "SU26", null, "PE 1", null,
                null, null, null, source(),
                List.of(question("stem", 1)), List.of(asset(PNG)), List.of()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_PAPER_TYPE_MISMATCH", ex.code());
    }

    @Test
    void rejectsExamCodeDisagreeingWithExplicitFields() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "MAE101", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 1, source(),
                List.of(question("stem", 1)), List.of(), List.of()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_PAPER_TYPE_MISMATCH", ex.code());
    }

    @Test
    void acceptsExamCodeThatDoesNotFollowTheConvention() {
        PaperWebhookRequest request = request(new PaperPayload(
                "TEST_EOS_Client_278333", "FE", "TEST", null, null, "t", null,
                20, new BigDecimal("28.50"), 1, source(),
                List.of(question("stem", 1)), List.of(), List.of()));

        assertEquals("TEST", validator.validate(request).subjectCode());
    }

    @Test
    void rejectsInvalidBase64() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 1, source(),
                List.of(new QuestionPayload("1", 1, "stem", 1, 1, BigDecimal.ONE,
                        List.of(new AssetPayload(0, "image/png", 3L, "a".repeat(64), "not-base64!!")),
                        List.of())),
                List.of(), List.of()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_IMAGE_INVALID_BASE64", ex.code());
    }

    @Test
    void rejectsNonImageBytes() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 1, source(),
                List.of(new QuestionPayload("1", 1, "stem", 1, 1, BigDecimal.ONE,
                        List.of(asset("hello world".getBytes())), List.of())),
                List.of(), List.of()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_IMAGE_TYPE_UNSUPPORTED", ex.code());
    }

    @Test
    void rejectsImageOverTheLimit() {
        ExamWebhookPayloadValidator tiny = new ExamWebhookPayloadValidator(4L, 200);

        PayloadTooLargeException ex = assertThrows(PayloadTooLargeException.class,
                () -> tiny.validate(feRequest(question("stem", 1))));
        assertEquals("WEBHOOK_TOO_LARGE", ex.code());
    }

    @Test
    void rejectsTooManyQuestions() {
        ExamWebhookPayloadValidator narrow = new ExamWebhookPayloadValidator(5_242_880L, 1);
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 2, source(),
                List.of(question("a", 1), questionWithId("b")), List.of(), List.of()));

        assertThrows(PayloadTooLargeException.class, () -> narrow.validate(request));
    }

    @Test
    void assignsDisplayNoByOrderWhenAbsent() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 2, source(),
                List.of(new QuestionPayload("a", null, "stem", 1, null, null,
                                List.of(asset(PNG)), List.of()),
                        new QuestionPayload("b", null, "stem", 1, null, null,
                                List.of(asset(PNG)), List.of())),
                List.of(), List.of()));

        IngestPaper paper = validator.validate(request);

        assertEquals(1, paper.questions().get(0).displayNo());
        assertEquals(2, paper.questions().get(1).displayNo());
    }

    @Test
    void rejectsAQuestionWithNeitherTextNorImage() {
        PaperWebhookRequest request = request(new PaperPayload(
                "SCM302_SU26_FE_553972", "FE", "SCM302", "SU26", null, "t", null,
                60, new BigDecimal("50.00"), 1, source(),
                List.of(new QuestionPayload("1", 1, "(Choose 1 answer)", 1, null, null,
                        List.of(), List.of())),
                List.of(), List.of()));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_QUESTION_EMPTY", ex.code());
    }

    @Test
    void rejectsAResourceWithoutAValidHash() {
        PaperWebhookRequest request = request(new PaperPayload(
                "PRJ301_SU26_PE_1", "PE", "PRJ301", "SU26", null, "PE 1", null,
                null, null, null, source(), List.of(), List.of(),
                List.of(new ResourcePayload(0, null, "a.zip", "application/zip",
                        10L, "short", "https://cdn.example.com/a.zip"))));

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_PAYLOAD_INVALID", ex.code());
    }

    @Test
    void rejectsAMissingPaper() {
        PaperWebhookRequest request = new PaperWebhookRequest(
                "evt-1", "exam.paper.upserted", Instant.now(), null);

        BadRequestException ex = assertThrows(BadRequestException.class, () -> validator.validate(request));
        assertEquals("WEBHOOK_PAYLOAD_INVALID", ex.code());
    }

    private static PaperWebhookRequest feRequest(QuestionPayload question) {
        return request(payload("FE", List.of(question), List.of(), List.of()));
    }

    private static PaperWebhookRequest request(PaperPayload paper) {
        return new PaperWebhookRequest(
                "3f2b9c14-8f0e-4a51-9b77-1c6d5e8a0f21", "exam.paper.upserted",
                Instant.parse("2026-08-31T03:54:59Z"), paper);
    }

    private static PaperPayload payload(String type, List<QuestionPayload> questions,
                                        List<AssetPayload> images, List<ResourcePayload> resources) {
        return new PaperPayload(
                "FE".equals(type) ? "SCM302_SU26_FE_553972" : "PRJ301_SU26_PE_1",
                type, "FE".equals(type) ? "SCM302" : "PRJ301", "SU26", null,
                "paper title", null, 60, new BigDecimal("50.00"),
                questions.isEmpty() ? null : questions.size(),
                source(), questions, images, resources);
    }

    private static PaperSourcePayload source() {
        return new PaperSourcePayload("eos-crawler", "553972",
                Instant.parse("2026-07-26T03:54:59Z"));
    }

    private static QuestionPayload question(String text, int expected) {
        return new QuestionPayload("1920809737", 1, text, expected, 11001,
                BigDecimal.ONE, List.of(asset(PNG)), List.of(1L, 2L, 3L, 4L));
    }

    private static QuestionPayload questionWithId(String externalId) {
        return new QuestionPayload(externalId, 2, "stem", 1, 11001,
                BigDecimal.ONE, List.of(asset(PNG)), List.of(5L, 6L));
    }

    private static AssetPayload asset(byte[] content) {
        return new AssetPayload(0, "image/png", (long) content.length,
                Sha256.hex(content), Base64.getEncoder().encodeToString(content));
    }
}
