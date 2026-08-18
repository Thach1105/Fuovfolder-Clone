package com.fuoverflow.grading.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.grading.api.dto.PaperDetailResponse;
import com.fuoverflow.grading.api.dto.PaperOptionResponse;
import com.fuoverflow.grading.api.dto.PaperQuestionResponse;
import com.fuoverflow.grading.api.dto.PaperSummaryResponse;
import com.fuoverflow.grading.domain.PaperStatus;
import com.fuoverflow.grading.persistence.GradingPaperAnswerEntity;
import com.fuoverflow.grading.persistence.GradingPaperAnswerRepository;
import com.fuoverflow.grading.persistence.GradingPaperEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionEntity;
import com.fuoverflow.grading.persistence.GradingPaperQuestionRepository;
import com.fuoverflow.grading.persistence.GradingPaperRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Read side of the paper bank. Admin-only: these responses carry the answer key. */
@Service
public class PaperQueryService {

    private static final List<String> FLAT_SECTIONS =
            List.of("GrammarQuestions", "FillBlankQuestions", "IndicateMQuestions");

    private final GradingPaperRepository paperRepository;
    private final GradingPaperQuestionRepository questionRepository;
    private final GradingPaperAnswerRepository answerRepository;
    private final ObjectMapper objectMapper;

    public PaperQueryService(GradingPaperRepository paperRepository,
                             GradingPaperQuestionRepository questionRepository,
                             GradingPaperAnswerRepository answerRepository,
                             ObjectMapper objectMapper) {
        this.paperRepository = paperRepository;
        this.questionRepository = questionRepository;
        this.answerRepository = answerRepository;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PaperSummaryResponse> list(String subjectCode, String status) {
        List<GradingPaperEntity> papers = subjectCode == null || subjectCode.isBlank()
                ? paperRepository.findByDeletedAtIsNullOrderByCreatedAtDesc()
                : paperRepository.findBySubjectCodeIgnoreCaseAndDeletedAtIsNullOrderByCreatedAtDesc(
                        subjectCode.trim());

        PaperStatus wanted = parseStatus(status);
        List<PaperSummaryResponse> result = new ArrayList<>();
        for (GradingPaperEntity paper : papers) {
            if (wanted != null && paper.getStatus() != wanted) {
                continue;
            }
            result.add(summary(paper, questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId())));
        }
        return List.copyOf(result);
    }

    @Transactional(readOnly = true)
    public PaperDetailResponse detail(UUID paperId) {
        GradingPaperEntity paper = requirePaper(paperId);
        List<GradingPaperQuestionEntity> questions =
                questionRepository.findByPaperIdOrderByDisplayNoAsc(paper.getId());

        Map<Long, List<GradingPaperAnswerEntity>> optionsByQid = new LinkedHashMap<>();
        for (GradingPaperAnswerEntity option
                : answerRepository.findByPaperIdOrderByQidAscOptionIndexAsc(paper.getId())) {
            optionsByQid.computeIfAbsent(option.getQid(), key -> new ArrayList<>()).add(option);
        }

        List<PaperQuestionResponse> payload = new ArrayList<>(questions.size());
        List<Long> unanswered = new ArrayList<>();
        for (GradingPaperQuestionEntity question : questions) {
            if (!question.isAnswered()) {
                unanswered.add(question.getQid());
            }
            List<PaperOptionResponse> options = new ArrayList<>();
            for (GradingPaperAnswerEntity option
                    : optionsByQid.getOrDefault(question.getQid(), List.of())) {
                options.add(new PaperOptionResponse(option.getQaid(), option.getOptionIndex(),
                        option.getOptionText(), option.isCorrect()));
            }
            payload.add(new PaperQuestionResponse(
                    question.getQid(),
                    question.getSection().name(),
                    question.getQType(),
                    question.getDisplayNo(),
                    question.getMark(),
                    question.getQuestionText(),
                    question.getImageSha256() != null,
                    question.getAnswerMode().name(),
                    question.getExpectedAnswerCount(),
                    question.getAnswerSource() == null ? null : question.getAnswerSource().name(),
                    question.isAnswered(),
                    List.copyOf(options)));
        }

        return new PaperDetailResponse(summary(paper, questions), List.copyOf(unanswered),
                List.copyOf(payload));
    }

    /**
     * Pulls the question image straight out of the stored payload, so the bank stays self-contained
     * and no image is duplicated into object storage.
     */
    @Transactional(readOnly = true)
    public byte[] questionImage(UUID paperId, long qid) {
        GradingPaperEntity paper = requirePaper(paperId);
        JsonNode payload = readPayload(paper);

        String base64 = findImageData(payload, qid);
        if (base64 == null || base64.isBlank()) {
            throw new NotFoundException("QUESTION_IMAGE_NOT_FOUND", "Câu " + qid + " không có ảnh.");
        }
        try {
            return Base64.getDecoder().decode(base64);
        } catch (IllegalArgumentException e) {
            throw new NotFoundException("QUESTION_IMAGE_NOT_FOUND",
                    "Ảnh của câu " + qid + " không đọc được.");
        }
    }

    @Transactional(readOnly = true)
    public List<String> readySubjectCodes() {
        return paperRepository.findByDeletedAtIsNullOrderByCreatedAtDesc().stream()
                .filter(paper -> paper.getStatus() == PaperStatus.READY)
                .map(GradingPaperEntity::getSubjectCode)
                .distinct()
                .sorted()
                .toList();
    }

    // ---------------------------------------------------------------- helpers

    private String findImageData(JsonNode payload, long qid) {
        for (String section : FLAT_SECTIONS) {
            String found = scanArray(payload.get(section), qid);
            if (found != null) {
                return found;
            }
        }
        JsonNode passages = payload.get("ReadingQuestions");
        if (passages != null && passages.isArray()) {
            for (JsonNode passage : passages) {
                String found = scanArray(passage.get("PassageQuestions"), qid);
                if (found != null) {
                    return found;
                }
            }
        }
        return null;
    }

    private String scanArray(JsonNode array, long qid) {
        if (array == null || !array.isArray()) {
            return null;
        }
        for (JsonNode node : array) {
            if (node.path("QID").asLong() == qid) {
                return node.path("ImageData").asText("");
            }
        }
        return null;
    }

    private JsonNode readPayload(GradingPaperEntity paper) {
        try {
            return objectMapper.readTree(paper.getRawPayload());
        } catch (JsonProcessingException e) {
            throw new BadRequestException("PAPER_PAYLOAD_UNREADABLE",
                    "Payload gốc của đề không đọc được.");
        }
    }

    private PaperStatus parseStatus(String status) {
        if (status == null || status.isBlank()) {
            return null;
        }
        try {
            return PaperStatus.valueOf(status.trim().toUpperCase());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException("INVALID_PAPER_STATUS",
                    "Trạng thái không hợp lệ: " + status);
        }
    }

    private GradingPaperEntity requirePaper(UUID paperId) {
        return paperRepository.findByIdAndDeletedAtIsNull(paperId)
                .orElseThrow(() -> new NotFoundException("PAPER_NOT_FOUND", "Không tìm thấy đề."));
    }

    private PaperSummaryResponse summary(GradingPaperEntity paper,
                                         List<GradingPaperQuestionEntity> questions) {
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
}
