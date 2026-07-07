package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminFeQuestionResponse;
import com.fuoverflow.exam.api.dto.CreateFeQuestionRequest;
import com.fuoverflow.exam.api.dto.FeOptionRequest;
import com.fuoverflow.exam.api.dto.ReorderRequest;
import com.fuoverflow.exam.api.dto.UpdateFeQuestionRequest;
import com.fuoverflow.exam.persistence.ExamFeOptionEntity;
import com.fuoverflow.exam.persistence.ExamFeOptionRepository;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ExamFeQuestionAdminService {
    private final ExamFeQuestionRepository questionRepository;
    private final ExamFeOptionRepository optionRepository;
    private final ExamSubjectRepository subjectRepository;
    private final ExamMediaService mediaService;
    private final ExamMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public ExamFeQuestionAdminService(
            ExamFeQuestionRepository questionRepository,
            ExamFeOptionRepository optionRepository,
            ExamSubjectRepository subjectRepository,
            ExamMediaService mediaService,
            ExamMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.subjectRepository = subjectRepository;
        this.mediaService = mediaService;
        this.urlResolver = urlResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminFeQuestionResponse> list(UUID subjectId) {
        requireSubject(subjectId);
        return questionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId).stream()
                .map(this::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminFeQuestionResponse get(UUID subjectId, UUID questionId) {
        return toAdmin(requireQuestion(subjectId, questionId));
    }

    @Transactional
    public AdminFeQuestionResponse create(UUID subjectId, CreateFeQuestionRequest request) {
        requireSubject(subjectId);
        Validated validated = validate(request.questionText(), request.questionImageUrls(), request.options());

        Instant now = Instant.now();
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : (int) questionRepository.countBySubjectIdAndDeletedAtIsNull(subjectId);

        UUID questionId = UUID.randomUUID();
        ExamFeQuestionEntity question = ExamFeQuestionEntity.create(
                questionId,
                subjectId,
                validated.questionText(),
                ExamJsonUtil.serialize(objectMapper, validated.imageKeys()),
                blankToNull(request.explanation()),
                validated.multipleCorrect(),
                sortOrder,
                now);
        questionRepository.save(question);
        saveOptions(questionId, validated.options(), now);
        linkImages(validated);
        return get(subjectId, questionId);
    }

    @Transactional
    public AdminFeQuestionResponse update(UUID subjectId, UUID questionId, UpdateFeQuestionRequest request) {
        ExamFeQuestionEntity question = requireQuestion(subjectId, questionId);
        List<ExamFeOptionEntity> existingOptions = optionRepository.findByQuestionIdOrderBySortOrderAsc(questionId);
        Validated validated = validate(request.questionText(), request.questionImageUrls(), request.options());

        List<String> oldImageKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls());
        cleanupReplacedImages(oldImageKeys, validated.imageKeys(), existingOptions, validated.options());

        Instant now = Instant.now();
        question.setQuestionText(validated.questionText());
        question.setQuestionImageUrls(ExamJsonUtil.serialize(objectMapper, validated.imageKeys()));
        question.setExplanation(blankToNull(request.explanation()));
        question.setMultipleCorrect(validated.multipleCorrect());
        if (request.sortOrder() != null) {
            question.setSortOrder(request.sortOrder());
        }
        question.setUpdatedAt(now);
        questionRepository.save(question);

        optionRepository.deleteByQuestionId(questionId);
        saveOptions(questionId, validated.options(), now);
        linkImages(validated);
        return get(subjectId, questionId);
    }

    @Transactional
    public void delete(UUID subjectId, UUID questionId) {
        ExamFeQuestionEntity question = requireQuestion(subjectId, questionId);
        List<ExamFeOptionEntity> options = optionRepository.findByQuestionIdOrderBySortOrderAsc(questionId);

        mediaService.deleteStoredReferences(ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls()));
        for (ExamFeOptionEntity option : options) {
            mediaService.deleteStoredReference(option.getOptionImageUrl());
        }

        Instant now = Instant.now();
        question.setDeletedAt(now);
        question.setUpdatedAt(now);
        questionRepository.save(question);
        optionRepository.deleteByQuestionId(questionId);
    }

    @Transactional
    public void reorder(UUID subjectId, ReorderRequest request) {
        requireSubject(subjectId);
        List<ExamFeQuestionEntity> questions =
                questionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subjectId);
        Set<UUID> existingIds = new HashSet<>();
        questions.forEach(q -> existingIds.add(q.getId()));
        if (request.ids().size() != existingIds.size() || !existingIds.containsAll(request.ids())) {
            throw new BadRequestException("INVALID_REORDER", "Question ID list must match all questions for this subject");
        }
        Instant now = Instant.now();
        int order = 0;
        for (UUID id : request.ids()) {
            ExamFeQuestionEntity q = questions.stream().filter(x -> x.getId().equals(id)).findFirst()
                    .orElseThrow(() -> new NotFoundException("EXAM_FE_QUESTION_NOT_FOUND", "Question not found"));
            q.setSortOrder(order++);
            q.setUpdatedAt(now);
            questionRepository.save(q);
        }
    }

    // --- internals ------------------------------------------------------------

    private void saveOptions(UUID questionId, List<ValidatedOption> options, Instant now) {
        int order = 0;
        for (ValidatedOption option : options) {
            int sortOrder = option.sortOrder() != null ? option.sortOrder() : order++;
            optionRepository.save(ExamFeOptionEntity.create(
                    UUID.randomUUID(), questionId, option.optionText(), option.optionImageUrl(),
                    option.correct(), sortOrder, now));
        }
    }

    private void linkImages(Validated validated) {
        mediaService.markLinkedAll(validated.imageKeys());
        for (ValidatedOption option : validated.options()) {
            mediaService.markLinked(option.optionImageUrl());
        }
    }

    private void cleanupReplacedImages(
            List<String> oldImageKeys,
            List<String> newImageKeys,
            List<ExamFeOptionEntity> oldOptions,
            List<ValidatedOption> newOptions) {
        Set<String> retained = new HashSet<>(newImageKeys != null ? newImageKeys : List.of());
        if (oldImageKeys != null) {
            for (String oldKey : oldImageKeys) {
                if (oldKey != null && !retained.contains(oldKey)) {
                    mediaService.unlinkStoredReference(oldKey);
                }
            }
        }
        Set<String> retainedOptionImages = new HashSet<>();
        for (ValidatedOption option : newOptions) {
            if (option.optionImageUrl() != null) {
                retainedOptionImages.add(option.optionImageUrl());
            }
        }
        for (ExamFeOptionEntity oldOption : oldOptions) {
            String key = oldOption.getOptionImageUrl();
            if (key != null && !retainedOptionImages.contains(key)) {
                mediaService.unlinkStoredReference(key);
            }
        }
    }

    private Validated validate(String questionText, List<String> imageUrls, List<FeOptionRequest> options) {
        String normalizedText = blankToNull(questionText);
        List<String> normalizedImages = new ArrayList<>();
        if (imageUrls != null) {
            for (String url : imageUrls) {
                String key = urlResolver.normalizeForStorage(url);
                if (key != null) {
                    normalizedImages.add(key);
                }
            }
        }
        if (normalizedText == null && normalizedImages.isEmpty()) {
            throw new BadRequestException("QUESTION_EMPTY", "Question must have text or at least one image");
        }
        if (options == null || options.size() < 2) {
            throw new BadRequestException("OPTIONS_TOO_FEW", "A question must have at least 2 options");
        }

        List<ValidatedOption> validatedOptions = new ArrayList<>();
        int correctCount = 0;
        int order = 0;
        for (FeOptionRequest option : options) {
            String optionText = blankToNull(option.optionText());
            String optionImage = urlResolver.normalizeForStorage(option.optionImageUrl());
            if (optionText == null && optionImage == null) {
                throw new BadRequestException("OPTION_EMPTY", "Each option must have text or an image");
            }
            boolean correct = Boolean.TRUE.equals(option.isCorrect());
            if (correct) {
                correctCount++;
            }
            validatedOptions.add(new ValidatedOption(optionText, optionImage, correct,
                    option.sortOrder() != null ? option.sortOrder() : order++));
        }
        if (correctCount < 1) {
            throw new BadRequestException("NO_CORRECT_OPTION", "At least one option must be marked correct");
        }
        return new Validated(normalizedText, normalizedImages, correctCount > 1, validatedOptions);
    }

    private AdminFeQuestionResponse toAdmin(ExamFeQuestionEntity q) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionImageUrls());
        List<AdminFeQuestionResponse.AdminFeOptionResponse> options =
                optionRepository.findByQuestionIdOrderBySortOrderAsc(q.getId()).stream()
                        .map(o -> new AdminFeQuestionResponse.AdminFeOptionResponse(
                                o.getId(), o.getOptionText(), urlResolver.plain(o.getOptionImageUrl()),
                                o.isCorrect(), o.getSortOrder()))
                        .toList();
        return new AdminFeQuestionResponse(
                q.getId(),
                q.getSubjectId(),
                q.getQuestionText(),
                urlResolver.plainAll(imageKeys),
                q.getExplanation(),
                q.isMultipleCorrect(),
                q.getSortOrder(),
                options,
                q.getCreatedAt(),
                q.getUpdatedAt());
    }

    private void requireSubject(UUID subjectId) {
        if (subjectRepository.findByIdAndDeletedAtIsNull(subjectId).isEmpty()) {
            throw new NotFoundException("EXAM_SUBJECT_NOT_FOUND", "Exam subject not found");
        }
    }

    private ExamFeQuestionEntity requireQuestion(UUID subjectId, UUID questionId) {
        return questionRepository.findByIdAndSubjectIdAndDeletedAtIsNull(questionId, subjectId)
                .orElseThrow(() -> new NotFoundException("EXAM_FE_QUESTION_NOT_FOUND", "Question not found"));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record Validated(String questionText, List<String> imageKeys, boolean multipleCorrect,
                             List<ValidatedOption> options) {
    }

    private record ValidatedOption(String optionText, String optionImageUrl, boolean correct, Integer sortOrder) {
    }
}
