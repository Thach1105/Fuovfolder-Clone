package com.fuoverflow.source.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.source.api.dto.AdminQuestionResponse;
import com.fuoverflow.source.api.dto.CreateQuestionRequest;
import com.fuoverflow.source.api.dto.QuestionOptionRequest;
import com.fuoverflow.source.api.dto.ReorderQuestionsRequest;
import com.fuoverflow.source.api.dto.UpdateQuestionRequest;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourceQuestionEntity;
import com.fuoverflow.source.persistence.SourceQuestionOptionEntity;
import com.fuoverflow.source.persistence.SourceQuestionOptionRepository;
import com.fuoverflow.source.persistence.SourceQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class SourceQuestionAdminService {
    private final SourceQuestionRepository questionRepository;
    private final SourceQuestionOptionRepository optionRepository;
    private final SourceCatalogItemRepository catalogRepository;
    private final SourceMediaService mediaService;
    private final SourceMediaUrlResolver urlResolver;

    public SourceQuestionAdminService(
            SourceQuestionRepository questionRepository,
            SourceQuestionOptionRepository optionRepository,
            SourceCatalogItemRepository catalogRepository,
            SourceMediaService mediaService,
            SourceMediaUrlResolver urlResolver) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.catalogRepository = catalogRepository;
        this.mediaService = mediaService;
        this.urlResolver = urlResolver;
    }

    @Transactional(readOnly = true)
    public List<AdminQuestionResponse> list(UUID catalogItemId) {
        requireCatalogItem(catalogItemId);
        return questionRepository.findByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAsc(catalogItemId).stream()
                .map(this::toAdminResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminQuestionResponse get(UUID catalogItemId, UUID questionId) {
        SourceQuestionEntity question = requireQuestion(catalogItemId, questionId);
        return toAdminResponse(question);
    }

    @Transactional
    public AdminQuestionResponse create(UUID catalogItemId, CreateQuestionRequest request) {
        requireCatalogItem(catalogItemId);
        ValidatedQuestion validated = validateQuestionInput(
                request.questionText(),
                request.questionImageUrl(),
                request.options());

        Instant now = Instant.now();
        int sortOrder = request.sortOrder() != null
                ? request.sortOrder()
                : (int) questionRepository.countByCatalogItemIdAndDeletedAtIsNull(catalogItemId);

        UUID questionId = UUID.randomUUID();
        SourceQuestionEntity question = SourceQuestionEntity.create(
                questionId,
                catalogItemId,
                validated.questionText(),
                validated.questionImageUrl(),
                blankToNull(request.explanation()),
                validated.multipleCorrect(),
                sortOrder,
                now);
        questionRepository.save(question);
        saveOptions(questionId, validated.options(), now);

        syncQuestionCount(catalogItemId);
        return get(catalogItemId, questionId);
    }

    @Transactional
    public AdminQuestionResponse update(UUID catalogItemId, UUID questionId, UpdateQuestionRequest request) {
        SourceQuestionEntity question = requireQuestion(catalogItemId, questionId);
        List<SourceQuestionOptionEntity> existingOptions =
                optionRepository.findByQuestionIdOrderBySortOrderAsc(questionId);

        ValidatedQuestion validated = validateQuestionInput(
                request.questionText(),
                request.questionImageUrl(),
                request.options());

        cleanupReplacedImages(
                question.getQuestionImageUrl(),
                urlResolver.normalizeForStorage(request.questionImageUrl()),
                existingOptions,
                validated.options());

        Instant now = Instant.now();
        question.setQuestionText(validated.questionText());
        question.setQuestionImageUrl(validated.questionImageUrl());
        question.setExplanation(blankToNull(request.explanation()));
        question.setMultipleCorrect(validated.multipleCorrect());
        if (request.sortOrder() != null) {
            question.setSortOrder(request.sortOrder());
        }
        question.setUpdatedAt(now);
        questionRepository.save(question);

        optionRepository.deleteByQuestionId(questionId);
        saveOptions(questionId, validated.options(), now);

        return get(catalogItemId, questionId);
    }

    @Transactional
    public void delete(UUID catalogItemId, UUID questionId) {
        SourceQuestionEntity question = requireQuestion(catalogItemId, questionId);
        List<SourceQuestionOptionEntity> options =
                optionRepository.findByQuestionIdOrderBySortOrderAsc(questionId);

        mediaService.deleteStoredReference(question.getQuestionImageUrl());
        for (SourceQuestionOptionEntity option : options) {
            mediaService.deleteStoredReference(option.getOptionImageUrl());
        }

        Instant now = Instant.now();
        question.setDeletedAt(now);
        question.setUpdatedAt(now);
        questionRepository.save(question);
        optionRepository.deleteByQuestionId(questionId);

        syncQuestionCount(catalogItemId);
    }

    @Transactional
    public void reorder(UUID catalogItemId, ReorderQuestionsRequest request) {
        requireCatalogItem(catalogItemId);
        List<SourceQuestionEntity> questions =
                questionRepository.findByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAsc(catalogItemId);
        Set<UUID> existingIds = new HashSet<>();
        for (SourceQuestionEntity question : questions) {
            existingIds.add(question.getId());
        }
        if (request.questionIds().size() != existingIds.size()
                || !existingIds.containsAll(request.questionIds())) {
            throw new BadRequestException("INVALID_REORDER", "Question ID list must match all questions for this item");
        }

        Instant now = Instant.now();
        int order = 0;
        for (UUID questionId : request.questionIds()) {
            SourceQuestionEntity question = questions.stream()
                    .filter(q -> q.getId().equals(questionId))
                    .findFirst()
                    .orElseThrow(() -> new NotFoundException("QUESTION_NOT_FOUND", "Question not found"));
            question.setSortOrder(order++);
            question.setUpdatedAt(now);
            questionRepository.save(question);
        }
    }

    private void saveOptions(UUID questionId, List<ValidatedOption> options, Instant now) {
        int order = 0;
        for (ValidatedOption option : options) {
            int sortOrder = option.sortOrder() != null ? option.sortOrder() : order++;
            optionRepository.save(SourceQuestionOptionEntity.create(
                    UUID.randomUUID(),
                    questionId,
                    option.optionText(),
                    option.optionImageUrl(),
                    option.correct(),
                    sortOrder,
                    now));
        }
    }

    private void syncQuestionCount(UUID catalogItemId) {
        long count = questionRepository.countByCatalogItemIdAndDeletedAtIsNull(catalogItemId);
        SourceCatalogItemEntity item = requireCatalogItem(catalogItemId);
        item.setQuestionCount((int) count);
        item.setUpdatedAt(Instant.now());
        catalogRepository.save(item);
    }

    private SourceCatalogItemEntity requireCatalogItem(UUID catalogItemId) {
        return catalogRepository.findByIdAndDeletedAtIsNull(catalogItemId)
                .orElseThrow(() -> new NotFoundException("CATALOG_NOT_FOUND", "Source item not found"));
    }

    private SourceQuestionEntity requireQuestion(UUID catalogItemId, UUID questionId) {
        return questionRepository.findByIdAndCatalogItemIdAndDeletedAtIsNull(questionId, catalogItemId)
                .orElseThrow(() -> new NotFoundException("QUESTION_NOT_FOUND", "Question not found"));
    }

    private ValidatedQuestion validateQuestionInput(
            String questionText,
            String questionImageUrl,
            List<QuestionOptionRequest> options) {
        String normalizedText = blankToNull(questionText);
        String normalizedImage = urlResolver.normalizeForStorage(questionImageUrl);
        if (normalizedText == null && normalizedImage == null) {
            throw new BadRequestException("QUESTION_EMPTY", "Question must have text or an image");
        }
        if (options == null || options.size() < 2) {
            throw new BadRequestException("OPTIONS_TOO_FEW", "A question must have at least 2 options");
        }

        List<ValidatedOption> validatedOptions = new ArrayList<>();
        int correctCount = 0;
        int order = 0;
        for (QuestionOptionRequest option : options) {
            String optionText = blankToNull(option.optionText());
            String optionImage = urlResolver.normalizeForStorage(option.optionImageUrl());
            if (optionText == null && optionImage == null) {
                throw new BadRequestException("OPTION_EMPTY", "Each option must have text or an image");
            }
            boolean correct = Boolean.TRUE.equals(option.isCorrect());
            if (correct) {
                correctCount++;
            }
            validatedOptions.add(new ValidatedOption(
                    optionText,
                    optionImage,
                    correct,
                    option.sortOrder() != null ? option.sortOrder() : order++));
        }
        if (correctCount < 1) {
            throw new BadRequestException("NO_CORRECT_OPTION", "At least one option must be marked correct");
        }

        return new ValidatedQuestion(normalizedText, normalizedImage, correctCount > 1, validatedOptions);
    }

    private void cleanupReplacedImages(
            String oldQuestionImage,
            String newQuestionImage,
            List<SourceQuestionOptionEntity> oldOptions,
            List<ValidatedOption> newOptions) {
        if (oldQuestionImage != null && !oldQuestionImage.equals(newQuestionImage)) {
            mediaService.deleteStoredReference(oldQuestionImage);
        }
        Set<String> retainedOptionImages = new HashSet<>();
        for (ValidatedOption option : newOptions) {
            if (option.optionImageUrl() != null) {
                retainedOptionImages.add(option.optionImageUrl());
            }
        }
        for (SourceQuestionOptionEntity oldOption : oldOptions) {
            String imageUrl = oldOption.getOptionImageUrl();
            if (imageUrl != null && !retainedOptionImages.contains(imageUrl)) {
                mediaService.deleteStoredReference(imageUrl);
            }
        }
    }

    private AdminQuestionResponse toAdminResponse(SourceQuestionEntity question) {
        return urlResolver.resolveAdmin(SourceQuestionMapper.toAdmin(
                question,
                optionRepository.findByQuestionIdOrderBySortOrderAsc(question.getId())));
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private record ValidatedQuestion(
            String questionText,
            String questionImageUrl,
            boolean multipleCorrect,
            List<ValidatedOption> options) {
    }

    private record ValidatedOption(
            String optionText,
            String optionImageUrl,
            boolean correct,
            Integer sortOrder) {
    }
}
