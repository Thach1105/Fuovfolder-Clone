package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.PayloadTooLargeException;
import com.fuoverflow.exam.api.dto.webhook.AssetPayload;
import com.fuoverflow.exam.api.dto.webhook.PaperPayload;
import com.fuoverflow.exam.api.dto.webhook.PaperWebhookRequest;
import com.fuoverflow.exam.api.dto.webhook.QuestionPayload;
import com.fuoverflow.exam.api.dto.webhook.ResourcePayload;
import com.fuoverflow.exam.config.ExamWebhookProperties;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.domain.IngestAsset;
import com.fuoverflow.exam.domain.IngestPaper;
import com.fuoverflow.exam.domain.IngestQuestion;
import com.fuoverflow.exam.domain.IngestResource;
import com.fuoverflow.exam.support.Sha256;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a webhook envelope into a validated {@link IngestPaper}, rejecting anything the paper bank
 * could not store faithfully.
 *
 * <p>Everything is rejected at the edge rather than skipped, because a silently dropped question
 * produces a paper that looks complete to a member but is missing content they paid for. Declared
 * metadata about the bytes ({@code mimeType}, {@code sizeBytes}, {@code sha256}) is never trusted:
 * all three are re-derived from the decoded image, so a sender that lies about a file cannot get a
 * non-image stored as one.
 */
@Component
public class ExamWebhookPayloadValidator {

    /** "(Choose 3 answers)" prefixes are metadata, not question content. */
    private static final Pattern CHOOSE_MARKER =
            Pattern.compile("^\\(Choose\\s+(\\d+)\\s+answers?\\)\\s*", Pattern.CASE_INSENSITIVE);

    public static final Pattern EXAM_CODE_PATTERN = Pattern.compile(
            "^(?<subject>\\w+)_(?<term>[A-Z]{2}\\d{2})_(?<type>FE|PE|PT|MID)_(?<id>\\d+)$");

    private static final Pattern SHA256_HEX = Pattern.compile("^[0-9a-fA-F]{64}$");

    private final long maxImageBytes;
    private final int maxQuestions;

    // @Autowired disambiguates: the second constructor exists for tests that pin the limits.
    @Autowired
    public ExamWebhookPayloadValidator(ExamWebhookProperties properties) {
        this(properties.maxImageBytesOrDefault(), properties.maxQuestionsOrDefault());
    }

    ExamWebhookPayloadValidator(long maxImageBytes, int maxQuestions) {
        this.maxImageBytes = maxImageBytes;
        this.maxQuestions = maxQuestions;
    }

    public IngestPaper validate(PaperWebhookRequest request) {
        if (request == null || request.paper() == null) {
            throw invalid("Payload thiếu đối tượng paper.");
        }
        if (isBlank(request.eventId())) {
            throw invalid("Payload thiếu eventId.");
        }

        PaperPayload paper = request.paper();
        ExamPaperType paperType = ExamPaperType.fromWire(paper.paperType());
        String subjectCode = trimmed(paper.subjectCode());
        if (subjectCode == null) {
            throw new BadRequestException("WEBHOOK_SUBJECT_CODE_REQUIRED", "Payload thiếu subjectCode.");
        }
        String examCode = trimmed(paper.examCode());
        if (examCode == null) {
            throw invalid("Payload thiếu examCode.");
        }
        String title = trimmed(paper.title());
        if (title == null) {
            throw invalid("Payload thiếu title.");
        }

        String term = trimmed(paper.term());
        String normalizedTerm = term != null ? term.toUpperCase(java.util.Locale.ROOT) : null;
        String campus = trimmed(paper.campus());
        verifyExamCodeAgreement(examCode, paperType, subjectCode, term);

        List<QuestionPayload> questions = orEmpty(paper.questions());
        List<AssetPayload> images = orEmpty(paper.images());
        List<ResourcePayload> resources = orEmpty(paper.resources());

        if (paperType == ExamPaperType.FE) {
            if (questions.isEmpty()) {
                throw new BadRequestException("WEBHOOK_FE_QUESTIONS_REQUIRED",
                        "Đề FE phải có ít nhất một câu hỏi.");
            }
            if (!images.isEmpty() || !resources.isEmpty()) {
                throw mismatch("Đề FE không nhận images/resources ở cấp đề.");
            }
        } else {
            if (images.isEmpty() && resources.isEmpty()) {
                throw new BadRequestException("WEBHOOK_PE_CONTENT_REQUIRED",
                        "Đề PE phải có ít nhất một ảnh đề hoặc một file tài nguyên.");
            }
            if (!questions.isEmpty()) {
                throw mismatch("Đề PE không nhận danh sách questions.");
            }
        }

        if (questions.size() > maxQuestions) {
            throw tooLarge("Số câu hỏi (" + questions.size() + ") vượt giới hạn " + maxQuestions + ".");
        }

        return new IngestPaper(
                examCode,
                paperType,
                subjectCode,
                normalizedTerm,
                trimmed(paper.retakeLabel()),
                title,
                trimmed(paper.description()),
                paper.durationMinutes(),
                paper.totalMark(),
                paper.declaredQuestionCount(),
                paper.source() != null ? trimmed(paper.source().system()) : null,
                paper.source() != null ? trimmed(paper.source().externalPaperId()) : null,
                validateQuestions(questions),
                validateAssets(images),
                validateResources(resources),
                campus != null ? campus.toUpperCase(java.util.Locale.ROOT) : null);
    }

    // --- questions ------------------------------------------------------------

    private List<IngestQuestion> validateQuestions(List<QuestionPayload> questions) {
        List<IngestQuestion> result = new ArrayList<>(questions.size());
        int position = 0;
        for (QuestionPayload question : questions) {
            position++;
            String externalId = trimmed(question.externalId());
            if (externalId == null) {
                throw invalid("Câu thứ " + position + " thiếu externalId.");
            }

            String rawText = question.questionText();
            Integer markerCount = expectedAnswerCount(rawText);
            String text = stripChooseMarker(rawText);
            List<IngestAsset> images = validateAssets(orEmpty(question.images()));
            if (text == null && images.isEmpty()) {
                throw new BadRequestException("WEBHOOK_QUESTION_EMPTY",
                        "Câu " + externalId + " không có nội dung text lẫn ảnh.");
            }

            result.add(new IngestQuestion(
                    externalId,
                    question.displayNo() != null ? question.displayNo() : position,
                    text,
                    question.expectedAnswerCount() != null ? question.expectedAnswerCount() : markerCount,
                    question.chapterId(),
                    question.mark(),
                    images,
                    orEmpty(question.answerOptionIds())));
        }
        return result;
    }

    // --- assets ---------------------------------------------------------------

    private List<IngestAsset> validateAssets(List<AssetPayload> assets) {
        List<IngestAsset> result = new ArrayList<>(assets.size());
        int position = 0;
        for (AssetPayload asset : assets) {
            byte[] content = decode(asset.contentBase64());
            if (content.length > maxImageBytes) {
                throw tooLarge("Ảnh " + content.length + " byte vượt giới hạn " + maxImageBytes + ".");
            }
            String mimeType = detectImageMime(content);
            result.add(new IngestAsset(
                    asset.sortOrder() != null ? asset.sortOrder() : position,
                    mimeType,
                    content.length,
                    Sha256.hex(content),
                    content));
            position++;
        }
        return result;
    }

    private static byte[] decode(String base64) {
        if (isBlank(base64)) {
            throw new BadRequestException("WEBHOOK_IMAGE_INVALID_BASE64", "Ảnh thiếu contentBase64.");
        }
        try {
            return Base64.getDecoder().decode(base64.trim());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("WEBHOOK_IMAGE_INVALID_BASE64",
                    "contentBase64 không phải base64 hợp lệ.");
        }
    }

    /**
     * Identifies the image from its leading bytes. The caller's declared {@code mimeType} is
     * ignored on purpose: it is the only thing standing between the store and a renamed
     * executable, and it costs nothing to check the real header.
     */
    private static String detectImageMime(byte[] content) {
        if (startsWith(content, new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A})) {
            return "image/png";
        }
        if (startsWith(content, new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF})) {
            return "image/jpeg";
        }
        if (startsWith(content, "GIF87a".getBytes()) || startsWith(content, "GIF89a".getBytes())) {
            return "image/gif";
        }
        if (content.length > 12
                && startsWith(content, "RIFF".getBytes())
                && content[8] == 'W' && content[9] == 'E' && content[10] == 'B' && content[11] == 'P') {
            return "image/webp";
        }
        throw new BadRequestException("WEBHOOK_IMAGE_TYPE_UNSUPPORTED",
                "Ảnh không phải PNG/JPEG/GIF/WEBP.");
    }

    private static boolean startsWith(byte[] data, byte[] prefix) {
        if (data.length < prefix.length) {
            return false;
        }
        for (int i = 0; i < prefix.length; i++) {
            if (data[i] != prefix[i]) {
                return false;
            }
        }
        return true;
    }

    // --- resources ------------------------------------------------------------

    private List<IngestResource> validateResources(List<ResourcePayload> resources) {
        List<IngestResource> result = new ArrayList<>(resources.size());
        int position = 0;
        for (ResourcePayload resource : resources) {
            String filename = trimmed(resource.filename());
            if (filename == null) {
                throw invalid("Resource thứ " + (position + 1) + " thiếu filename.");
            }
            String sourceUrl = trimmed(resource.sourceUrl());
            if (sourceUrl == null) {
                throw invalid("Resource " + filename + " thiếu sourceUrl.");
            }
            String sha256 = trimmed(resource.sha256());
            if (sha256 == null || !SHA256_HEX.matcher(sha256).matches()) {
                throw invalid("Resource " + filename + " thiếu sha256 hợp lệ (64 ký tự hex).");
            }

            result.add(new IngestResource(
                    resource.sortOrder() != null ? resource.sortOrder() : position,
                    trimmed(resource.folderLabel()),
                    filename,
                    trimmed(resource.mimeType()),
                    resource.sizeBytes() != null ? resource.sizeBytes() : 0L,
                    sha256.toLowerCase(),
                    sourceUrl));
            position++;
        }
        return result;
    }

    // --- exam code ------------------------------------------------------------

    /**
     * Only checks a code that follows the naming convention. Production data already contains
     * codes that do not ({@code TEST_EOS_Client_278333}), so parsing cannot be the source of
     * truth — the explicit fields are, and this is a consistency check on top of them.
     */
    private void verifyExamCodeAgreement(
            String examCode, ExamPaperType paperType, String subjectCode, String term) {
        Matcher matcher = EXAM_CODE_PATTERN.matcher(examCode);
        if (!matcher.matches()) {
            return;
        }
        if (!matcher.group("subject").equalsIgnoreCase(subjectCode)) {
            throw mismatch("examCode mang môn " + matcher.group("subject")
                    + " nhưng subjectCode là " + subjectCode + ".");
        }
        // Only FE and PE are storable types, so a PT/MID code has nothing to disagree with:
        // such a paper is ingested as FE rather than refused.
        String codeType = matcher.group("type");
        if (("FE".equals(codeType) || "PE".equals(codeType))
                && !codeType.equals(paperType.dbValue())) {
            throw mismatch("examCode mang loại " + codeType
                    + " nhưng paperType là " + paperType.dbValue() + ".");
        }
        if (term != null && !matcher.group("term").equalsIgnoreCase(term)) {
            throw mismatch("examCode mang kỳ " + matcher.group("term")
                    + " nhưng term là " + term + ".");
        }
    }

    // --- helpers --------------------------------------------------------------

    private static Integer expectedAnswerCount(String rawText) {
        if (rawText == null) {
            return null;
        }
        Matcher matcher = CHOOSE_MARKER.matcher(rawText.trim());
        return matcher.find() ? Integer.valueOf(matcher.group(1)) : null;
    }

    private static String stripChooseMarker(String rawText) {
        if (rawText == null) {
            return null;
        }
        String normalized = rawText.replace("\r\n", "\n").trim();
        String stripped = CHOOSE_MARKER.matcher(normalized).replaceFirst("").trim();
        return stripped.isEmpty() ? null : stripped;
    }

    private static <T> List<T> orEmpty(List<T> value) {
        return value == null ? List.of() : value;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String trimmed(String value) {
        return isBlank(value) ? null : value.trim();
    }

    private static BadRequestException invalid(String message) {
        return new BadRequestException("WEBHOOK_PAYLOAD_INVALID", message);
    }

    private static BadRequestException mismatch(String message) {
        return new BadRequestException("WEBHOOK_PAPER_TYPE_MISMATCH", message);
    }

    private static PayloadTooLargeException tooLarge(String message) {
        return new PayloadTooLargeException("WEBHOOK_TOO_LARGE", message);
    }
}
