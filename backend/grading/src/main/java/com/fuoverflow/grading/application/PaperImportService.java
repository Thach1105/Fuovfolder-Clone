package com.fuoverflow.grading.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.grading.api.dto.PaperPreviewResponse;
import com.fuoverflow.grading.api.dto.PaperSummaryResponse;
import com.fuoverflow.grading.api.dto.PreviewOption;
import com.fuoverflow.grading.api.dto.PreviewQuestion;
import com.fuoverflow.grading.domain.NormalizedOption;
import com.fuoverflow.grading.domain.NormalizedPaper;
import com.fuoverflow.grading.domain.NormalizedQuestion;
import com.fuoverflow.grading.domain.PaperSection;
import com.fuoverflow.grading.domain.PaperStatus;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import com.fuoverflow.grading.support.PaperFingerprint;
import com.fuoverflow.grading.support.Sha256;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Converts an exam payload into a stored paper, in two deliberately separate steps.
 *
 * <p>{@link #preview(JsonNode)} parses and validates without writing anything, so the admin UI can
 * show the whole paper first. {@link #importPayload} then requires the fingerprint the preview
 * returned, which makes "review before saving" a backend rule rather than a UI convention.
 */
@Service
public class PaperImportService {

    private final GradingPaperRepository paperRepository;
    private final GradingPaperQuestionRepository questionRepository;
    private final GradingPaperAnswerRepository answerRepository;
    private final PaperPayloadNormalizer normalizer;
    private final ObjectMapper objectMapper;

    public PaperImportService(GradingPaperRepository paperRepository,
                              GradingPaperQuestionRepository questionRepository,
                              GradingPaperAnswerRepository answerRepository,
                              PaperPayloadNormalizer normalizer,
                              ObjectMapper objectMapper) {
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.normalizer = normalizer;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public PaperPreviewResponse preview(JsonNode payload) {
        requirePayload(payload);
        NormalizedPaper normalized = normalizer.normalize(payload);
        String fingerprint = PaperFingerprint.of(normalized);

        Optional<GradingPaperEntity> existing =
                paperRepository.findByFingerprintAndDeletedAtIsNull(fingerprint);

        String collision = "NONE";
        UUID collisionPaperId = null;
        int existingAnsweredCount = 0;
        if (existing.isPresent()) {
            GradingPaperEntity paper = existing.get();
            collisionPaperId = paper.getId();
            if (paper.getStatus() == PaperStatus.READY) {
                collision = "EXISTING_PUBLISHED";
            } else {
                collision = "EXISTING_DRAFT";
                existingAnsweredCount = countAnswered(paper.getId());
            }
        }

        List<PreviewQuestion> questions = new ArrayList<>(normalized.questions().size());
        List<String> warnings = new ArrayList<>();
        for (NormalizedQuestion question : normalized.questions()) {
            List<PreviewOption> options = new ArrayList<>(question.options().size());
            for (NormalizedOption option : question.options()) {
                options.add(new PreviewOption(option.qaid(), option.optionIndex(), option.text()));
            }
            questions.add(new PreviewQuestion(
                    question.qid(),
                    question.displayNo(),
                    question.section().name(),
                    question.answerMode().name(),
                    question.expectedAnswerCount(),
                    question.questionText(),
                    question.imageBase64(),
                    options));
            warnings.addAll(warningsFor(question));
        }

        return new PaperPreviewResponse(
                normalized.examCode(),
                normalized.subjectCode(),
                normalized.durationMinutes(),
                normalized.totalMark(),
                normalized.questions().size(),
                fingerprint,
                collision,
                collisionPaperId,
                existingAnsweredCount,
                List.copyOf(questions),
                List.copyOf(warnings));
    }

    @Transactional
    public PaperSummaryResponse importPayload(UUID adminId, JsonNode payload, UUID sourcePayloadId,
                                              String confirmedFingerprint) {
        if (confirmedFingerprint == null || confirmedFingerprint.isBlank()) {
            throw new BadRequestException("FINGERPRINT_REQUIRED", "Cần xem trước đề rồi mới lưu.");
        }
        requirePayload(payload);

        NormalizedPaper normalized = normalizer.normalize(payload);
        String fingerprint = PaperFingerprint.of(normalized);
        if (!fingerprint.equals(confirmedFingerprint)) {
            throw new BadRequestException("FINGERPRINT_MISMATCH",
                    "Payload đã thay đổi so với lúc xem trước. Hãy xem lại rồi lưu.");
        }

        String rawJson = writeJson(payload);
        String payloadSha256 = Sha256.hexUtf8(rawJson);

        Optional<GradingPaperEntity> existing =
                paperRepository.findByFingerprintAndDeletedAtIsNull(fingerprint)
                        .or(() -> paperRepository.findByPayloadSha256AndDeletedAtIsNull(payloadSha256));
        if (existing.isPresent()) {
            GradingPaperEntity paper = existing.get();
            if (paper.getStatus() == PaperStatus.READY) {
                throw new ConflictException("PAPER_ALREADY_PUBLISHED",
                        "Đề đã phát hành. Xoá đề cũ trước khi nhập lại.");
            }
            // Re-importing a draft must never discard answers the admin has already entered.
            return summary(paper);
        }

        GradingPaperEntity paper = paperRepository.save(GradingPaperEntity.draft(
                normalized.examCode(),
                normalized.subjectCode(),
                fingerprint,
                normalized.questions().size(),
                normalized.durationMinutes(),
                normalized.totalMark(),
                rawJson,
                payloadSha256,
                sourcePayloadId,
                adminId));

        List<GradingPaperQuestionEntity> questions = new ArrayList<>(normalized.questions().size());
        List<GradingPaperAnswerEntity> options = new ArrayList<>();
        for (NormalizedQuestion question : normalized.questions()) {
            String imageSha256 = question.imageBase64() == null
                    ? null
                    : Sha256.hex(decodeImage(question));
            GradingPaperQuestionEntity entity = GradingPaperQuestionEntity.of(
                    paper.getId(),
                    question.qid(),
                    question.section(),
                    question.qType(),
                    question.displayNo(),
                    question.mark(),
                    question.chapterId(),
                    question.questionText(),
                    imageSha256,
                    contentSha256(question, imageSha256),
                    question.answerMode(),
                    question.expectedAnswerCount());
            questions.add(entity);
            for (NormalizedOption option : question.options()) {
                options.add(GradingPaperAnswerEntity.of(
                        paper.getId(),
                        entity.getId(),
                        question.qid(),
                        option.qaid(),
                        option.optionIndex(),
                        option.text(),
                        option.text() == null ? null : Sha256.hexUtf8(option.text())));
            }
        }
        questionRepository.saveAll(questions);
        answerRepository.saveAll(options);

        return new PaperSummaryResponse(
                paper.getId(),
                paper.getExamCode(),
                paper.getSubjectCode(),
                paper.getStatus().name(),
                questions.size(),
                0,
                questions.size(),
                paper.getCreatedAt(),
                paper.getPublishedAt());
    }

    // ---------------------------------------------------------------- helpers

    private void requirePayload(JsonNode payload) {
        if (payload == null || payload.isNull()) {
            throw new BadRequestException("PAYLOAD_REQUIRED", "Cần payload JSON của đề thi.");
        }
    }

    private List<String> warningsFor(NormalizedQuestion question) {
        List<String> warnings = new ArrayList<>(2);
        if (question.questionText() == null && question.imageBase64() == null) {
            warnings.add("Câu " + question.qid() + " không có nội dung (không có text lẫn ảnh).");
        }
        if (question.section() == PaperSection.FILL_BLANK || question.section() == PaperSection.MATCH) {
            warnings.add("Câu " + question.qid() + " dạng " + question.section()
                    + " chưa chấm điểm được.");
        } else if (question.expectedAnswerCount() == null) {
            warnings.add("Câu " + question.qid() + " không đọc được số đáp án cần chọn.");
        }
        return warnings;
    }

    /** Falls back to the qid so the NOT NULL column always has a value. */
    private String contentSha256(NormalizedQuestion question, String imageSha256) {
        if (question.questionText() != null) {
            return Sha256.hexUtf8(question.questionText());
        }
        if (imageSha256 != null) {
            return imageSha256;
        }
        return Sha256.hexUtf8("qid:" + question.qid());
    }

    private byte[] decodeImage(NormalizedQuestion question) {
        try {
            return Base64.getDecoder().decode(question.imageBase64());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("PAYLOAD_IMAGE_INVALID",
                    "Câu " + question.qid() + " có ImageData không phải base64 hợp lệ.");
        }
    }

    private int countAnswered(UUID paperId) {
        return (int) questionRepository.findByPaperIdOrderByDisplayNoAsc(paperId).stream()
                .filter(GradingPaperQuestionEntity::isAnswered)
                .count();
    }

    private PaperSummaryResponse summary(GradingPaperEntity paper) {
        List<GradingPaperQuestionEntity> questions =
                questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId());
        int answered = (int) questions.stream().filter(GradingPaperQuestionEntity::isAnswered).count();
        return new PaperSummaryResponse(
                paper.getId(),
                paper.getExamCode(),
                paper.getSubjectCode(),
                paper.getStatus().name(),
                questions.size(),
                answered,
                questions.size() - answered,
                paper.getCreatedAt(),
                paper.getPublishedAt());
    }

    private String writeJson(JsonNode payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException e) {
            throw new BadRequestException("PAYLOAD_INVALID", "Không đọc được payload JSON.");
        }
    }
}
