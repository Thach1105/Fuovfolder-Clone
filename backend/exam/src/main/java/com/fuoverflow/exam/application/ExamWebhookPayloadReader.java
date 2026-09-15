package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Reads a webhook body in either accepted shape and hands back the canonical envelope.
 *
 * <p>Two formats arrive on the same endpoint: the canonical envelope, and the raw EOS exam file
 * the crawler already produces. The shape decides which — no extra header for the sender to
 * remember and get wrong.
 *
 * <p>Both the controller and the ingest worker read through here. The worker re-parses the body it
 * stored, so if detection lived only at the edge an EOS delivery would be accepted and then fail
 * in the background.
 */
@Component
public class ExamWebhookPayloadReader {

    /** Any one of these at the top level marks a body as an EOS exam file. */
    private static final List<String> EOS_QUESTION_SECTIONS = List.of(
            "GrammarQuestions", "FillBlankQuestions", "IndicateMQuestions",
            "ReadingQuestions", "MatchQuestions");

    /** Leaves room for the {@code "#<index>"} suffix inside {@code event_id varchar(120)}. */
    private static final int MAX_BATCH_EVENT_ID_LENGTH = 100;

    private final ObjectMapper objectMapper;
    private final EosPayloadAdapter eosAdapter;

    public ExamWebhookPayloadReader(ObjectMapper objectMapper, EosPayloadAdapter eosAdapter) {
        this.objectMapper = objectMapper;
        this.eosAdapter = eosAdapter;
    }

    public PaperWebhookRequest read(String rawBody) {
        JsonNode tree = parseTree(rawBody);

        if (tree.hasNonNull("paper")) {
            return readCanonical(rawBody);
        }
        if (looksLikeEos(tree)) {
            return eosAdapter.adapt(tree, rawBody);
        }
        throw new BadRequestException("WEBHOOK_PAYLOAD_UNRECOGNIZED",
                "Body không phải envelope chuẩn (thiếu \"paper\") cũng không phải file đề EOS "
                        + "(thiếu \"ExamCode\" kèm một mảng câu hỏi).");
    }

    /**
     * One paper of a delivery, paired with the single-paper envelope stored for it. A batch slice is
     * re-serialized rather than the sender's original bytes: the signature was verified against the
     * whole body before the split, and a stored row must hold exactly one paper because the ingest
     * worker re-parses it through {@link #read(String)}.
     */
    public record DeliveredPaper(PaperWebhookRequest request, String payloadJson) {
    }

    /** Splits any accepted body shape into one delivery per paper. */
    public List<DeliveredPaper> readAll(String rawBody, int maxPapers) {
        JsonNode tree = parseTree(rawBody);

        if (tree.hasNonNull("paper") && tree.hasNonNull("papers")) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_AMBIGUOUS",
                    "Body chỉ được có \"paper\" hoặc \"papers\", không được có cả hai.");
        }
        if (tree.hasNonNull("papers")) {
            return readBatch(tree, maxPapers);
        }
        return List.of(new DeliveredPaper(read(rawBody), rawBody));
    }

    private List<DeliveredPaper> readBatch(JsonNode tree, int maxPapers) {
        JsonNode papers = tree.get("papers");
        if (papers == null || !papers.isArray() || papers.isEmpty()) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID",
                    "\"papers\" phải là một mảng không rỗng.");
        }
        if (papers.size() > maxPapers) {
            throw new BadRequestException("WEBHOOK_BATCH_TOO_LARGE",
                    "Batch có " + papers.size() + " đề, vượt giới hạn " + maxPapers + ".");
        }

        String eventId = tree.path("eventId").asText("").trim();
        if (eventId.isEmpty()) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Payload thiếu eventId.");
        }
        if (eventId.length() > MAX_BATCH_EVENT_ID_LENGTH) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID",
                    "eventId của batch không được dài quá " + MAX_BATCH_EVENT_ID_LENGTH + " ký tự.");
        }

        List<DeliveredPaper> delivered = new ArrayList<>(papers.size());
        for (int index = 0; index < papers.size(); index++) {
            ObjectNode envelope = objectMapper.createObjectNode();
            envelope.put("eventId", eventId + "#" + index);
            if (tree.hasNonNull("eventType")) {
                envelope.set("eventType", tree.get("eventType"));
            }
            if (tree.hasNonNull("sentAt")) {
                envelope.set("sentAt", tree.get("sentAt"));
            }
            envelope.set("paper", papers.get(index));

            String slice = writeSlice(envelope);
            delivered.add(new DeliveredPaper(readCanonical(slice), slice));
        }
        return delivered;
    }

    private String writeSlice(ObjectNode envelope) {
        try {
            return objectMapper.writeValueAsString(envelope);
        } catch (Exception e) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Không đọc được body.");
        }
    }

    /** True for a payload carrying an exam code and at least one question-bearing section. */
    private static boolean looksLikeEos(JsonNode tree) {
        if (!tree.hasNonNull("ExamCode")) {
            return false;
        }
        return EOS_QUESTION_SECTIONS.stream().anyMatch(section -> tree.path(section).isArray());
    }

    private JsonNode parseTree(String rawBody) {
        if (rawBody == null || rawBody.isBlank()) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Body rỗng.");
        }
        try {
            JsonNode tree = objectMapper.readTree(rawBody);
            if (tree == null || !tree.isObject()) {
                throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID",
                        "Body phải là một JSON object.");
            }
            return tree;
        } catch (BadRequestException e) {
            throw e;
        } catch (Exception e) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Body không phải JSON hợp lệ.");
        }
    }

    private PaperWebhookRequest readCanonical(String rawBody) {
        try {
            return objectMapper
                    .readerFor(PaperWebhookRequest.class)
                    .without(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .readValue(rawBody);
        } catch (Exception e) {
            throw new BadRequestException("WEBHOOK_PAYLOAD_INVALID", "Không đọc được body.");
        }
    }
}
