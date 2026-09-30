package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.exam.support.Sha256;
import java.util.regex.Pattern;

/** Converts the PE client export to a canonical paper without persisting client credentials/binaries. */
final class PePayloadAdapter {
    private static final Pattern TERM = Pattern.compile("[A-Za-z]{2}\\d{2}");

    static ObjectNode adapt(JsonNode raw, ObjectMapper mapper) {
        String testName = raw.path("testName").asText("").trim();
        if (testName.isEmpty()) throw invalid("Thiếu testName của đề PE.");
        if (!raw.path("paperNo").isIntegralNumber() || !raw.path("paperNo").canConvertToInt() || raw.path("paperNo").asInt() < 1)
            throw invalid("paperNo phải là số nguyên dương.");
        String[] parts = testName.split("[-_]+");
        int subjectIndex = parts[0].equalsIgnoreCase("PE") ? 1 : 0;
        if (parts.length <= subjectIndex) throw invalid("Không xác định được mã môn PE.");
        String term = null;
        for (String part : parts) if (TERM.matcher(part).matches()) term = part.toUpperCase(java.util.Locale.ROOT);
        ObjectNode envelope = mapper.createObjectNode();
        ObjectNode paper = envelope.putObject("paper");
        String code = testName + "_PaperNo" + raw.path("paperNo").asInt();
        paper.put("examCode", code);
        paper.put("paperType", "PE");
        paper.put("subjectCode", parts[subjectIndex]);
        if (term != null) paper.put("term", term);
        paper.put("title", code);
        paper.putObject("source").put("system", "pe-client");
        var images = paper.putArray("images");
        JsonNode pages = raw.path("paperImage");
        if (!pages.isMissingNode() && !pages.isArray()) throw invalid("paperImage phải là mảng.");
        if (raw.has("numberOfPage") && (!raw.path("numberOfPage").isIntegralNumber() || !raw.path("numberOfPage").canConvertToInt()
                || raw.path("numberOfPage").asInt() != pages.size()))
            throw invalid("numberOfPage không khớp số ảnh; không nhập đề thiếu trang.");
        for (JsonNode page : pages) {
            if (!page.isTextual() || page.asText().isBlank()) throw invalid("Ảnh PE rỗng hoặc sai định dạng.");
            images.addObject().put("sortOrder", images.size()).put("contentBase64", page.asText());
        }
        var resources = paper.putArray("resources");
        JsonNode materials = raw.path("givenMaterials");
        if (!materials.isMissingNode() && !materials.isArray()) throw invalid("givenMaterials phải là mảng.");
        int index = 0;
        for (JsonNode material : materials) {
            if (!material.isObject() || !material.path("given").isTextual()
                    || material.path("given").asText().isBlank()) throw invalid("Tài nguyên PE thiếu given Base64.");
            index++;
            if (!material.path("questionNo").isIntegralNumber() || !material.path("questionNo").canConvertToInt()
                    || material.path("questionNo").asInt() < 1) throw invalid("questionNo phải là số nguyên dương.");
            int question = material.path("questionNo").asInt();
            resources.addObject().put("sortOrder", index - 1).put("folderLabel", "Câu " + question)
                    .put("filename", "question-" + question + "-" + index + ".zip")
                    .put("mimeType", "application/zip").put("contentBase64", material.path("given").asText());
        }
        envelope.put("eventId", "pe:" + Sha256.hexUtf8(paper.toString()));
        envelope.put("eventType", "exam.paper.upserted");
        return envelope;
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException("WEBHOOK_PE_PAYLOAD_INVALID", message);
    }
}
