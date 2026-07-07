package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.api.dto.PublicFeQuestionResponse;
import com.fuoverflow.exam.api.dto.PublicPeItemResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectCardResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectDetailResponse;
import com.fuoverflow.exam.persistence.ExamFeOptionEntity;
import com.fuoverflow.exam.persistence.ExamFeOptionRepository;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPeItemEntity;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceEntity;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExamCatalogQueryService {
    private final ExamSubjectRepository subjectRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamFeOptionRepository feOptionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPeResourceRepository peResourceRepository;
    private final ExamAccessGuard accessGuard;
    private final ExamMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public ExamCatalogQueryService(
            ExamSubjectRepository subjectRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamFeOptionRepository feOptionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPeResourceRepository peResourceRepository,
            ExamAccessGuard accessGuard,
            ExamMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.feQuestionRepository = feQuestionRepository;
        this.feOptionRepository = feOptionRepository;
        this.peItemRepository = peItemRepository;
        this.peResourceRepository = peResourceRepository;
        this.accessGuard = accessGuard;
        this.urlResolver = urlResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PublicSubjectCardResponse> listActive() {
        return subjectRepository.findByActiveTrueAndDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(this::toCard)
                .toList();
    }

    @Transactional(readOnly = true)
    public PublicSubjectDetailResponse getDetail(String idOrCode, UUID userId) {
        ExamSubjectEntity subject = resolveActive(idOrCode);
        boolean member = accessGuard.hasActiveMembership(userId);
        return new PublicSubjectDetailResponse(
                subject.getId(),
                subject.getCode(),
                subject.getTitle(),
                subject.getDescription(),
                subject.getCategorySlug(),
                subject.getCardColor(),
                urlResolver.signed(subject.getCoverImageUrl()),
                subject.getViewCount(),
                (int) feQuestionRepository.countBySubjectIdAndDeletedAtIsNull(subject.getId()),
                (int) peItemRepository.countBySubjectIdAndDeletedAtIsNull(subject.getId()),
                subject.getFePreviewCount(),
                member);
    }

    /**
     * FE questions. Members get the full bank; non-members get only the first
     * {@code fePreviewCount} questions (answers included, per product decision) with
     * {@code locked=true}.
     */
    @Transactional(readOnly = true)
    public PublicFeQuestionListResponse listFeQuestions(String idOrCode, UUID userId) {
        ExamSubjectEntity subject = resolveActive(idOrCode);
        boolean member = accessGuard.hasActiveMembership(userId);
        List<ExamFeQuestionEntity> all =
                feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subject.getId());
        int total = all.size();
        int previewCount = Math.max(0, subject.getFePreviewCount());

        List<ExamFeQuestionEntity> visible = member
                ? all
                : all.subList(0, Math.min(previewCount, total));

        boolean locked = !member;
        List<PublicFeQuestionResponse> questions = visible.stream()
                .map(q -> toPublicQuestion(q, locked))
                .toList();
        return new PublicFeQuestionListResponse(locked, total, previewCount, questions);
    }

    /** PE papers + downloadable resources. Members only. */
    @Transactional(readOnly = true)
    public List<PublicPeItemResponse> listPeItems(String idOrCode, UUID userId) {
        ExamSubjectEntity subject = resolveActive(idOrCode);
        accessGuard.requireActiveMembership(userId);
        return peItemRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subject.getId()).stream()
                .map(this::toPublicPeItem)
                .toList();
    }

    // --- mapping helpers ------------------------------------------------------

    private PublicSubjectCardResponse toCard(ExamSubjectEntity s) {
        return new PublicSubjectCardResponse(
                s.getId(),
                s.getCode(),
                s.getTitle(),
                s.getCategorySlug(),
                s.getCardColor(),
                urlResolver.signed(s.getCoverImageUrl()),
                s.getViewCount(),
                (int) feQuestionRepository.countBySubjectIdAndDeletedAtIsNull(s.getId()),
                (int) peItemRepository.countBySubjectIdAndDeletedAtIsNull(s.getId()));
    }

    private PublicFeQuestionResponse toPublicQuestion(ExamFeQuestionEntity q, boolean preview) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionImageUrls());
        List<PublicFeQuestionResponse.PublicFeOptionResponse> options =
                feOptionRepository.findByQuestionIdOrderBySortOrderAsc(q.getId()).stream()
                        .map(this::toPublicOption)
                        .toList();
        return new PublicFeQuestionResponse(
                q.getId(),
                q.getQuestionText(),
                urlResolver.signedAll(imageKeys),
                q.getExplanation(),
                q.isMultipleCorrect(),
                q.getSortOrder(),
                preview,
                options);
    }

    private PublicFeQuestionResponse.PublicFeOptionResponse toPublicOption(ExamFeOptionEntity o) {
        return new PublicFeQuestionResponse.PublicFeOptionResponse(
                o.getId(),
                o.getOptionText(),
                urlResolver.signed(o.getOptionImageUrl()),
                o.isCorrect());
    }

    private PublicPeItemResponse toPublicPeItem(ExamPeItemEntity item) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, item.getExamImageUrls());
        List<PublicPeItemResponse.PublicPeResourceResponse> resources =
                peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()).stream()
                        .map(this::toPublicResource)
                        .toList();
        return new PublicPeItemResponse(
                item.getId(),
                item.getTitle(),
                item.getDescription(),
                urlResolver.signedAll(imageKeys),
                item.getSortOrder(),
                resources);
    }

    private PublicPeItemResponse.PublicPeResourceResponse toPublicResource(ExamPeResourceEntity r) {
        return new PublicPeItemResponse.PublicPeResourceResponse(
                r.getId(),
                r.getFolderLabel(),
                r.getOriginalFilename(),
                r.getMimeType(),
                r.getSizeBytes(),
                r.getSortOrder(),
                "/api/v1/exam/pe/resources/" + r.getId() + "/download");
    }

    // --- resolution -----------------------------------------------------------

    ExamSubjectEntity resolveActive(String idOrCode) {
        return tryParseUuid(idOrCode)
                .flatMap(subjectRepository::findByIdAndDeletedAtIsNull)
                .or(() -> subjectRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(idOrCode.trim()))
                .filter(ExamSubjectEntity::isActive)
                .orElseThrow(() -> new NotFoundException("EXAM_SUBJECT_NOT_FOUND", "Exam subject not found"));
    }

    private static Optional<UUID> tryParseUuid(String value) {
        try {
            return Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException ex) {
            return Optional.empty();
        }
    }
}
