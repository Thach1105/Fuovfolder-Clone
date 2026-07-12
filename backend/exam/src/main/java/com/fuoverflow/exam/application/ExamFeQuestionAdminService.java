package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminFeQuestionResponse;
import com.fuoverflow.exam.api.dto.CreateFeQuestionRequest;
import com.fuoverflow.exam.api.dto.ReorderRequest;
import com.fuoverflow.exam.api.dto.UpdateFeQuestionRequest;
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
    private final ExamSubjectRepository subjectRepository;
    private final ExamMediaService mediaService;
    private final ExamMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public ExamFeQuestionAdminService(
            ExamFeQuestionRepository questionRepository,
            ExamSubjectRepository subjectRepository,
            ExamMediaService mediaService,
            ExamMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
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
        Validated validated = validate(request.questionText(), request.questionImageUrls(), request.questionBlurUrls());

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
                ExamJsonUtil.serialize(objectMapper, validated.blurKeys()),
                sortOrder,
                now);
        questionRepository.save(question);
        mediaService.markLinkedAll(validated.imageKeys());
        return get(subjectId, questionId);
    }

    @Transactional
    public AdminFeQuestionResponse update(UUID subjectId, UUID questionId, UpdateFeQuestionRequest request) {
        ExamFeQuestionEntity question = requireQuestion(subjectId, questionId);
        Validated validated = validate(request.questionText(), request.questionImageUrls(), request.questionBlurUrls());

        List<String> oldImageKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls());
        List<String> oldBlurKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionBlurUrls());
        cleanupReplacedImages(oldImageKeys, oldBlurKeys, validated.imageKeys(), validated.blurKeys());

        Instant now = Instant.now();
        question.setQuestionText(validated.questionText());
        question.setQuestionImageUrls(ExamJsonUtil.serialize(objectMapper, validated.imageKeys()));
        question.setQuestionBlurUrls(ExamJsonUtil.serialize(objectMapper, validated.blurKeys()));
        if (request.sortOrder() != null) {
            question.setSortOrder(request.sortOrder());
        }
        question.setUpdatedAt(now);
        questionRepository.save(question);
        mediaService.markLinkedAll(validated.imageKeys());
        return get(subjectId, questionId);
    }

    @Transactional
    public void delete(UUID subjectId, UUID questionId) {
        ExamFeQuestionEntity question = requireQuestion(subjectId, questionId);
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls());
        List<String> blurKeys = ExamJsonUtil.deserialize(objectMapper, question.getQuestionBlurUrls());
        mediaService.deletePairedAll(imageKeys, blurKeys);

        Instant now = Instant.now();
        question.setDeletedAt(now);
        question.setUpdatedAt(now);
        questionRepository.save(question);
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

    private void cleanupReplacedImages(
            List<String> oldImageKeys, List<String> oldBlurKeys,
            List<String> newImageKeys, List<String> newBlurKeys) {
        Set<String> retained = new HashSet<>(newImageKeys != null ? newImageKeys : List.of());
        if (oldImageKeys != null) {
            for (int i = 0; i < oldImageKeys.size(); i++) {
                String oldKey = oldImageKeys.get(i);
                if (oldKey != null && !retained.contains(oldKey)) {
                    String oldBlur = (oldBlurKeys != null && i < oldBlurKeys.size()) ? oldBlurKeys.get(i) : null;
                    mediaService.deletePaired(oldKey, oldBlur);
                }
            }
        }
    }

    private Validated validate(String questionText, List<String> imageUrls, List<String> blurUrls) {
        String normalizedText = blankToNull(questionText);
        List<String> normalizedImages = new ArrayList<>();
        List<String> normalizedBlurs = new ArrayList<>();
        if (imageUrls != null) {
            for (int i = 0; i < imageUrls.size(); i++) {
                String key = urlResolver.normalizeForStorage(imageUrls.get(i));
                if (key == null) {
                    continue;
                }
                String blurUrl = (blurUrls != null && i < blurUrls.size()) ? blurUrls.get(i) : null;
                normalizedImages.add(key);
                normalizedBlurs.add(urlResolver.normalizeForStorage(blurUrl));
            }
        }
        if (normalizedText == null && normalizedImages.isEmpty()) {
            throw new BadRequestException("QUESTION_EMPTY", "Question must have text or at least one image");
        }
        return new Validated(normalizedText, normalizedImages, normalizedBlurs);
    }

    private AdminFeQuestionResponse toAdmin(ExamFeQuestionEntity q) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionImageUrls());
        List<String> blurKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionBlurUrls());
        return new AdminFeQuestionResponse(
                q.getId(),
                q.getSubjectId(),
                q.getQuestionText(),
                urlResolver.plainAll(imageKeys),
                urlResolver.plainAll(blurKeys),
                q.getSortOrder(),
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

    private record Validated(String questionText, List<String> imageKeys, List<String> blurKeys) {
    }
}
