package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.api.dto.PublicFeQuestionResponse;
import com.fuoverflow.exam.api.dto.PublicPeItemResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectCardResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectDetailResponse;
import com.fuoverflow.exam.persistence.ExamCommentRepository;
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

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class ExamCatalogQueryService {
    private static final String FE_QUESTION_SUBJECT_TYPE = "fe_question";

    private final ExamSubjectRepository subjectRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPeResourceRepository peResourceRepository;
    private final ExamCommentRepository commentRepository;
    private final ExamAccessGuard accessGuard;
    private final ExamMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public ExamCatalogQueryService(
            ExamSubjectRepository subjectRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPeResourceRepository peResourceRepository,
            ExamCommentRepository commentRepository,
            ExamAccessGuard accessGuard,
            ExamMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.feQuestionRepository = feQuestionRepository;
        this.peItemRepository = peItemRepository;
        this.peResourceRepository = peResourceRepository;
        this.commentRepository = commentRepository;
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
                subject.getFePreviewImageCount(),
                member);
    }

    /**
     * FE questions. All posts are visible to all users; images are gated per
     * {@code fePreviewImageCount} for non-members (first N images full, rest blurred).
     */
    @Transactional(readOnly = true)
    public PublicFeQuestionListResponse listFeQuestions(String idOrCode, UUID userId) {
        ExamSubjectEntity subject = resolveActive(idOrCode);
        boolean member = accessGuard.hasActiveMembership(userId);
        List<ExamFeQuestionEntity> all =
                feQuestionRepository.findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAsc(subject.getId());
        int total = all.size();
        int previewImageCount = Math.max(0, subject.getFePreviewImageCount());

        // The preview budget is spent across the whole paper, not per post. Counting per post
        // would hand a non-member every image of a paper whose questions carry one image each -
        // exactly the shape a webhook-ingested EOS paper has.
        int[] previewBudget = {previewImageCount};
        List<PublicFeQuestionResponse> questions = all.stream()
                .map(q -> toPublicQuestion(q, member, previewBudget))
                .toList();
        return new PublicFeQuestionListResponse(!member, total, previewImageCount, questions);
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

    /**
     * @param previewBudget single-element holder for the remaining free images of this paper;
     *                      decremented as images are handed out full.
     */
    private PublicFeQuestionResponse toPublicQuestion(
            ExamFeQuestionEntity q, boolean isMember, int[] previewBudget) {
        List<String> imageKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionImageUrls());
        List<String> blurKeys = ExamJsonUtil.deserialize(objectMapper, q.getQuestionBlurUrls());
        int totalImages = imageKeys.size();

        List<PublicFeQuestionResponse.PublicImageItem> images = new ArrayList<>();
        for (int i = 0; i < totalImages; i++) {
            boolean readable = isMember || previewBudget[0] > 0;
            if (!isMember && readable) {
                previewBudget[0]--;
            }
            if (readable) {
                images.add(new PublicFeQuestionResponse.PublicImageItem(
                        i, urlResolver.signed(imageKeys.get(i)), "full"));
            } else {
                String blurKey = (i < blurKeys.size()) ? blurKeys.get(i) : null;
                String blurUrl = (blurKey != null) ? urlResolver.signed(blurKey) : null;
                images.add(new PublicFeQuestionResponse.PublicImageItem(i, blurUrl, "blur"));
            }
        }

        int commentCount = (int) commentRepository.countBySubjectTypeAndSubjectIdAndDeletedAtIsNull(
                FE_QUESTION_SUBJECT_TYPE, q.getId());

        return new PublicFeQuestionResponse(
                q.getId(),
                q.getQuestionText(),
                totalImages,
                images,
                q.getSortOrder(),
                commentCount,
                q.getViewCount(),
                q.getCreatedAt());
    }

    @Transactional
    public void incrementViewCount(UUID questionId) {
        feQuestionRepository.incrementViewCount(questionId);
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
