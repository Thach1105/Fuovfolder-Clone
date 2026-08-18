package com.fuoverflow.grading.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.grading.domain.AnswerMode;
import com.fuoverflow.grading.domain.NormalizedOption;
import com.fuoverflow.grading.domain.NormalizedPaper;
import com.fuoverflow.grading.domain.NormalizedQuestion;
import com.fuoverflow.grading.domain.PaperSection;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a raw exam delivery payload into a shape the paper bank can store.
 *
 * <p>The two payload shapes seen in production differ structurally, not just in content: optional
 * keys vanish entirely rather than being null, the question node field set varies per section, and
 * one shape puts content in {@code Text} while the other puts it in {@code ImageData}. Everything is
 * therefore read defensively, and anything unrecognised is rejected outright rather than skipped —
 * silently dropping a question would produce a paper that grades to a wrong score.
 */
@Component
public class PaperPayloadNormalizer {

    /** "(Choose 3 answers)" prefixes are metadata, not question content. */
    private static final Pattern CHOOSE_MARKER =
            Pattern.compile("^\\(Choose\\s+(\\d+)\\s+answers?\\)\\s*", Pattern.CASE_INSENSITIVE);

    /** QD key -> the section whose element count that key declares. */
    private static final Map<String, PaperSection> QD_KEY_TO_SECTION = Map.of(
            "MultipleChoices", PaperSection.GRAMMAR,
            "IndicateMistake", PaperSection.INDICATE_MISTAKE,
            "FillBlank", PaperSection.FILL_BLANK,
            "Matching", PaperSection.MATCH,
            "Reading", PaperSection.READING);

    public NormalizedPaper normalize(JsonNode payload) {
        if (payload == null || !payload.isObject()) {
            throw new BadRequestException("PAYLOAD_INVALID", "Payload không phải một JSON object.");
        }

        String examCode = text(payload.get("ExamCode"));
        if (examCode == null) {
            throw new BadRequestException("PAYLOAD_EXAM_CODE_REQUIRED", "Payload thiếu ExamCode.");
        }

        List<NormalizedQuestion> questions = new ArrayList<>();
        Set<Long> seenQids = new LinkedHashSet<>();
        int[] displayNo = {0};

        readFlatSection(payload, "GrammarQuestions", PaperSection.GRAMMAR, questions, seenQids, displayNo);
        readFlatSection(payload, "FillBlankQuestions", PaperSection.FILL_BLANK, questions, seenQids, displayNo);
        readFlatSection(payload, "IndicateMQuestions", PaperSection.INDICATE_MISTAKE, questions, seenQids, displayNo);
        readPassages(payload, questions, seenQids, displayNo);
        readMatches(payload, questions, seenQids, displayNo);

        verifyCounts(payload, questions);

        return new NormalizedPaper(
                examCode,
                subjectCodeOf(examCode),
                integer(payload.get("Duration")),
                decimal(payload.get("Mark")),
                payload.path("NoOfQuestion").asInt(questions.size()),
                List.copyOf(questions));
    }

    // ---------------------------------------------------------------- sections

    private void readFlatSection(JsonNode payload, String field, PaperSection section,
                                 List<NormalizedQuestion> sink, Set<Long> seenQids, int[] displayNo) {
        for (JsonNode node : array(payload, field)) {
            sink.add(readQuestion(node, section, seenQids, displayNo));
        }
    }

    private void readPassages(JsonNode payload, List<NormalizedQuestion> sink,
                              Set<Long> seenQids, int[] displayNo) {
        for (JsonNode passage : array(payload, "ReadingQuestions")) {
            for (JsonNode node : array(passage, "PassageQuestions")) {
                sink.add(readQuestion(node, PaperSection.READING, seenQids, displayNo));
            }
        }
    }

    /**
     * Matching questions carry a different field set: keyed by MID, no QuestionAnswers, and their
     * answer key lives in Solution (which arrives masked as "#;#;#").
     */
    private void readMatches(JsonNode payload, List<NormalizedQuestion> sink,
                             Set<Long> seenQids, int[] displayNo) {
        for (JsonNode node : array(payload, "MatchQuestions")) {
            long mid = node.path("MID").asLong();
            requireUniqueQid(mid, seenQids);
            sink.add(new NormalizedQuestion(
                    mid,
                    PaperSection.MATCH,
                    null,
                    ++displayNo[0],
                    decimalOrZero(node.get("Mark")),
                    integer(node.get("ChapterId")),
                    text(node.get("ColumnA")),
                    null,
                    AnswerMode.TEXT,
                    null,
                    List.of()));
        }
    }

    private NormalizedQuestion readQuestion(JsonNode node, PaperSection section,
                                            Set<Long> seenQids, int[] displayNo) {
        long qid = node.path("QID").asLong();
        requireUniqueQid(qid, seenQids);

        Integer qType = integer(node.get("QType"));
        verifyQType(section, qType, qid);

        String rawText = text(node.get("Text"));
        Integer expectedAnswerCount = expectedAnswerCount(rawText);
        String questionText = stripChooseMarker(rawText);

        List<NormalizedOption> options = new ArrayList<>();
        JsonNode answers = node.get("QuestionAnswers");
        if (answers != null && answers.isArray()) {
            int index = 0;
            for (JsonNode answer : answers) {
                options.add(new NormalizedOption(
                        answer.path("QAID").asLong(), index++, text(answer.get("Text"))));
            }
        }

        return new NormalizedQuestion(
                qid,
                section,
                qType,
                ++displayNo[0],
                decimalOrZero(node.get("Mark")),
                integer(node.get("ChapterId")),
                questionText,
                text(node.get("ImageData")),
                answerMode(section, expectedAnswerCount),
                expectedAnswerCount,
                List.copyOf(options));
    }

    // ---------------------------------------------------------------- validation

    /**
     * Only (section, QType) pairs observed in real payloads are accepted. Passage questions carry no
     * QType at all, which is why null is valid for READING and invalid everywhere else.
     */
    private void verifyQType(PaperSection section, Integer qType, long qid) {
        boolean ok = switch (section) {
            case GRAMMAR -> qType != null && qType == 1;
            case INDICATE_MISTAKE -> qType != null && qType == 2;
            case FILL_BLANK -> qType != null && (qType == 5 || qType == 6);
            case READING -> qType == null;
            case MATCH -> true;
        };
        if (!ok) {
            throw new BadRequestException("PAYLOAD_UNSUPPORTED_QTYPE",
                    "Câu " + qid + " thuộc " + section + " có QType không hỗ trợ: " + qType);
        }
    }

    /**
     * QD declares a count per section, but not all in the same unit: Reading counts passages while
     * every other key counts questions. Comparing a flat parsed total against NoOfQuestion would
     * wrongly reject any paper that has reading passages.
     */
    private void verifyCounts(JsonNode payload, List<NormalizedQuestion> questions) {
        JsonNode qd = payload.get("QD");
        if (qd == null || !qd.isObject()) {
            return;
        }

        int declaredTotal = 0;
        Map<PaperSection, Integer> declaredPerSection = new LinkedHashMap<>();
        for (Map.Entry<String, PaperSection> entry : QD_KEY_TO_SECTION.entrySet()) {
            int declared = qd.path(entry.getKey()).asInt(0);
            declaredTotal += declared;
            declaredPerSection.put(entry.getValue(), declared);
        }

        int noOfQuestion = payload.path("NoOfQuestion").asInt(declaredTotal);
        if (declaredTotal != noOfQuestion) {
            throw new BadRequestException("PAYLOAD_COUNT_MISMATCH",
                    "Tổng QD (" + declaredTotal + ") khác NoOfQuestion (" + noOfQuestion + ").");
        }

        for (Map.Entry<PaperSection, Integer> entry : declaredPerSection.entrySet()) {
            PaperSection section = entry.getKey();
            int actual = section == PaperSection.READING
                    ? array(payload, "ReadingQuestions").size()
                    : (int) questions.stream().filter(q -> q.section() == section).count();
            if (actual != entry.getValue()) {
                throw new BadRequestException("PAYLOAD_SECTION_COUNT_MISMATCH",
                        "Section " + section + " có " + actual + " phần tử nhưng QD khai "
                                + entry.getValue() + ".");
            }
        }
    }

    private void requireUniqueQid(long qid, Set<Long> seenQids) {
        if (!seenQids.add(qid)) {
            throw new BadRequestException("PAYLOAD_DUPLICATE_QID", "QID " + qid + " xuất hiện nhiều lần.");
        }
    }

    // ---------------------------------------------------------------- helpers

    private AnswerMode answerMode(PaperSection section, Integer expectedAnswerCount) {
        if (section == PaperSection.FILL_BLANK || section == PaperSection.MATCH) {
            return AnswerMode.TEXT;
        }
        return expectedAnswerCount != null && expectedAnswerCount > 1 ? AnswerMode.MULTI : AnswerMode.SINGLE;
    }

    private Integer expectedAnswerCount(String rawText) {
        if (rawText == null) {
            return null;
        }
        Matcher matcher = CHOOSE_MARKER.matcher(rawText);
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private String stripChooseMarker(String rawText) {
        if (rawText == null) {
            return null;
        }
        String stripped = CHOOSE_MARKER.matcher(rawText).replaceFirst("").trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private String subjectCodeOf(String examCode) {
        int separator = examCode.indexOf('_');
        return separator > 0 ? examCode.substring(0, separator) : examCode;
    }

    private List<JsonNode> array(JsonNode parent, String field) {
        JsonNode node = parent == null ? null : parent.get(field);
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<JsonNode> items = new ArrayList<>(node.size());
        node.forEach(items::add);
        return items;
    }

    private String text(JsonNode node) {
        if (node == null || node.isNull()) {
            return null;
        }
        String value = node.asText().replace("\r\n", "\n").trim();
        return value.isEmpty() ? null : value;
    }

    private Integer integer(JsonNode node) {
        return node == null || node.isNull() ? null : node.asInt();
    }

    private BigDecimal decimal(JsonNode node) {
        return node == null || node.isNull() ? null : BigDecimal.valueOf(node.asDouble());
    }

    private BigDecimal decimalOrZero(JsonNode node) {
        BigDecimal value = decimal(node);
        return value == null ? BigDecimal.ZERO : value;
    }
}
