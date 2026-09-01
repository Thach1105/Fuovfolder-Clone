package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import org.springframework.stereotype.Component;

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
