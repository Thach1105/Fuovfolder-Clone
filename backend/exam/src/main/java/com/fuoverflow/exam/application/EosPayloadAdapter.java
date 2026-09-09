package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.api.dto.webhook.AssetPayload;
import com.fuoverflow.exam.api.dto.webhook.PaperPayload;
import com.fuoverflow.exam.api.dto.webhook.PaperSourcePayload;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import com.fuoverflow.exam.api.dto.webhook.QuestionPayload;
import com.fuoverflow.exam.support.ExamCodeParser;
import com.fuoverflow.exam.support.Sha256;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

/**
 * Turns a raw EOS exam delivery into the canonical webhook envelope.
 *
 * <p>Exists so the crawler can POST the file it already has. Everything the canonical shape asks
 * for is derived here — the exam code carries subject, term and type; the idempotency key comes
 * from the code plus a hash of the body — so the sender adds nothing to its own payload.
 *
 * <p>The adapter only renames and regroups. Validation, image decoding and storage all continue
 * through the same path a canonical delivery takes, so the two formats cannot drift apart.
 */
@Component
public class EosPayloadAdapter {

    /** Sections that hold questions directly. Reading passages nest theirs one level deeper. */
    private static final List<String> FLAT_SECTIONS =
            List.of("GrammarQuestions", "FillBlankQuestions", "IndicateMQuestions");

    /** {@code event_id} is varchar(120); a 16-hex digest plus separators leaves this much for the code. */
    private static final int MAX_EVENT_ID = 120;
    private static final int DIGEST_CHARS = 16;

    public PaperWebhookRequest adapt(JsonNode payload, String rawBody) {
        String examCode = text(payload.get("ExamCode"));
        if (examCode == null) {
            throw new BadRequestException("WEBHOOK_EOS_EXAM_CODE_REQUIRED",
                    "Payload EOS thiếu ExamCode.");
        }

        List<QuestionPayload> questions = readQuestions(payload);
        if (questions.isEmpty()) {
            throw new BadRequestException("WEBHOOK_EOS_NO_IMAGE_QUESTIONS",
                    "Không có câu hỏi nào mang ImageData nên đề sẽ rỗng.");
        }

        ExamCodeParser.Parsed parsed = ExamCodeParser.parse(examCode);

        PaperPayload paper = new PaperPayload(
                examCode,
                // The table stores FE or PE only; a progress test is still a set of MCQs, so it
                // lands as FE rather than being refused.
                "PE".equals(parsed.paperType()) ? "PE" : "FE",
                parsed.subjectCode(),
                parsed.term(),
                null,
                examCode,
                null,
                integer(payload.get("Duration")),
                decimal(payload.get("Mark")),
                integer(payload.get("NoOfQuestion")),
                new PaperSourcePayload("eos", parsed.externalPaperId(), Instant.now()),
                questions,
                List.of(),
                List.of(),
                parsed.campus());

        return new PaperWebhookRequest(
                buildEventId(examCode, rawBody),
                "exam.paper.upserted",
                Instant.now(),
                paper);
    }

    // --- questions ------------------------------------------------------------

    /**
     * Collects every section that can carry a question, keeping only those with an image. A
     * question whose {@code Text} is just "(Choose N answer)" and has no image would be rejected
     * downstream as empty, and dropping the whole delivery for it helps nobody.
     */
    private List<QuestionPayload> readQuestions(JsonNode payload) {
        List<JsonNode> raw = new ArrayList<>();
        for (String section : FLAT_SECTIONS) {
            array(payload, section).forEach(raw::add);
        }
        for (JsonNode passage : array(payload, "ReadingQuestions")) {
            array(passage, "PassageQuestions").forEach(raw::add);
        }

        List<QuestionPayload> questions = new ArrayList<>();
        int displayNo = 0;
        for (JsonNode node : raw) {
            String image = text(node.get("ImageData"));
            if (image == null) {
                continue;
            }
            questions.add(new QuestionPayload(
                    externalId(node),
                    ++displayNo,
                    text(node.get("Text")),
                    null,
                    integer(node.get("ChapterId")),
                    decimal(node.get("Mark")),
                    List.of(new AssetPayload(
                            0,
                            "image/png",
                            longValue(node.get("ImageSize")),
                            null,
                            image)),
                    answerOptionIds(node)));
        }
        return questions;
    }

    private static String externalId(JsonNode node) {
        JsonNode qid = node.get("QID");
        if (qid == null || qid.isNull()) {
            qid = node.get("MID");
        }
        return qid == null || qid.isNull() ? null : qid.asText();
    }

    private static List<Long> answerOptionIds(JsonNode node) {
        List<Long> ids = new ArrayList<>();
        for (JsonNode answer : array(node, "QuestionAnswers")) {
            JsonNode qaid = answer.get("QAID");
            if (qaid != null && !qaid.isNull()) {
                ids.add(qaid.asLong());
            }
        }
        return ids;
    }

    // --- idempotency ----------------------------------------------------------

    /**
     * EOS payloads carry no event id, so one is derived: the same file always produces the same
     * key, which makes an accidental redelivery a no-op. A genuinely edited paper produces a new
     * key and is then reconciled by the paper fingerprint instead.
     */
    private static String buildEventId(String examCode, String rawBody) {
        String digest = Sha256.hexUtf8(rawBody == null ? "" : rawBody).substring(0, DIGEST_CHARS);
        int room = MAX_EVENT_ID - "eos:".length() - 1 - DIGEST_CHARS;
        String code = examCode.length() > room ? examCode.substring(0, room) : examCode;
        return "eos:" + code + ":" + digest;
    }

    // --- helpers --------------------------------------------------------------

    private static Iterable<JsonNode> array(JsonNode parent, String field) {
        JsonNode node = parent == null ? null : parent.get(field);
        return node != null && node.isArray() ? node : List.of();
    }

    private static String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText();
        return value.isBlank() ? null : value;
    }

    private static Integer integer(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private static Long longValue(JsonNode node) {
        return node == null || node.isNull() ? null : node.asLong();
    }

    private static BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() ? null : BigDecimal.valueOf(node.asDouble());
    }
}
