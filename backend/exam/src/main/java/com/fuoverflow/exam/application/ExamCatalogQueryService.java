package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.PublicFeQuestionListResponse;
import com.fuoverflow.exam.api.dto.PublicFeQuestionResponse;
import com.fuoverflow.exam.api.dto.PublicPaperDetailResponse;
import com.fuoverflow.exam.api.dto.PublicPaperSummaryResponse;
import com.fuoverflow.exam.api.dto.PublicPeItemResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectCardResponse;
import com.fuoverflow.exam.api.dto.PublicSubjectDetailResponse;
import com.fuoverflow.exam.persistence.ExamCommentRepository;
import com.fuoverflow.exam.domain.ExamPaperStatus;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
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
    private final ExamPaperRepository paperRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPeResourceRepository peResourceRepository;
    private final ExamCommentRepository commentRepository;
    private final ExamAccessGuard accessGuard;
    private final ExamMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public ExamCatalogQueryService(
            ExamSubjectRepository subjectRepository,
            ExamPaperRepository paperRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPeResourceRepository peResourceRepository,
            ExamCommentRepository commentRepository,
            ExamAccessGuard accessGuard,
            ExamMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.subjectRepository = subjectRepository;
        this.paperRepository = paperRepository;
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
        List<ExamPaperEntity> papers = publishedPapers(subject.getId());
        return new PublicSubjectDetailResponse(
                subject.getId(),
                subject.getCode(),
                subject.getTitle(),
                subject.getDescription(),
                subject.getCategorySlug(),
                subject.getCardColor(),
                urlResolver.signed(subject.getCoverImageUrl()),
                subject.getViewCount(),
                (int) countOfType(papers, ExamPaperType.FE),
                (int) countOfType(papers, ExamPaperType.PE),
                subject.getFePreviewImageCount(),
                member,
                papers.stream().map(this::toPaperSummary).toList(),
                relatedCards(subject));
    }

    /** Full content of one published paper. Members only, same gate PE items use. */
    @Transactional(readOnly = true)
    public PublicPaperDetailResponse getPaper(UUID paperId, UUID userId) {
        accessGuard.requireActiveMembership(userId);
        ExamPaperEntity paper = paperRepository.findByIdAndDeletedAtIsNull(paperId)
                .filter(ExamPaperEntity::isPublished)
                .orElseThrow(() -> new NotFoundException("EXAM_PAPER_NOT_FOUND", "Exam paper not found"));
        ExamSubjectEntity subject = subjectRepository.findByIdAndDeletedAtIsNull(paper.getSubjectId())
                .orElseThrow(() -> new NotFoundException("EXAM_SUBJECT_NOT_FOUND", "Exam subject not found"));

        List<String> imageKeys = new ArrayList<>();
        List<PublicPeItemResponse.PublicPeResourceResponse> resources = new ArrayList<>();
        if (paper.paperTypeEnum() == ExamPaperType.FE) {
            for (ExamFeQuestionEntity question
                    : feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paperId)) {
                imageKeys.addAll(ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls()));
            }
        } else {
            for (ExamPeItemEntity item
                    : peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paperId)) {
                imageKeys.addAll(ExamJsonUtil.deserialize(objectMapper, item.getExamImageUrls()));
                peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId())
                        .forEach(resource -> resources.add(toPublicResource(resource)));
            }
        }

        return new PublicPaperDetailResponse(
                paper.getId(),
                subject.getId(),
                subject.getCode(),
                paper.getPaperType(),
                paper.getTerm(),
                paper.getRetakeLabel(),
                paper.getTitle(),
                paper.getDescription(),
                urlResolver.signedAll(imageKeys),
                resources);
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
        String published = ExamPaperStatus.PUBLISHED.dbValue();
        PublicSubjectCardResponse.LatestPaperSummary latestPaper = paperRepository
                .findFirstBySubjectIdAndStatusAndDeletedAtIsNullOrderByCreatedAtDesc(s.getId(), published)
                .map(p -> new PublicSubjectCardResponse.LatestPaperSummary(
                        p.getExamCode(), p.getPaperType(), p.getCreatedAt()))
                .orElse(null);
        return new PublicSubjectCardResponse(
                s.getId(),
                s.getCode(),
                s.getTitle(),
                s.getCategorySlug(),
                s.getCardColor(),
                urlResolver.signed(s.getCoverImageUrl()),
                s.getViewCount(),
                (int) paperRepository.countBySubjectIdAndPaperTypeAndStatusAndDeletedAtIsNull(
                        s.getId(), ExamPaperType.FE.dbValue(), published),
                (int) paperRepository.countBySubjectIdAndPaperTypeAndStatusAndDeletedAtIsNull(
                        s.getId(), ExamPaperType.PE.dbValue(), published),
                s.getCurriculumTerm(),
                latestPaper);
    }

    private List<ExamPaperEntity> publishedPapers(UUID subjectId) {
        return paperRepository.findBySubjectIdAndStatusAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(
                subjectId, ExamPaperStatus.PUBLISHED.dbValue());
    }

    private static long countOfType(List<ExamPaperEntity> papers, ExamPaperType type) {
        return papers.stream().filter(p -> type.dbValue().equals(p.getPaperType())).count();
    }

    private PublicPaperSummaryResponse toPaperSummary(ExamPaperEntity paper) {
        int imageCount;
        int resourceCount = 0;
        if (paper.paperTypeEnum() == ExamPaperType.FE) {
            imageCount = (int) feQuestionRepository.countByPaperIdAndDeletedAtIsNull(paper.getId());
        } else {
            int images = 0;
            for (ExamPeItemEntity item
                    : peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paper.getId())) {
                images += ExamJsonUtil.deserialize(objectMapper, item.getExamImageUrls()).size();
                resourceCount += peResourceRepository
                        .findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()).size();
            }
            imageCount = images;
        }
        return new PublicPaperSummaryResponse(
                paper.getId(),
                paper.getPaperType(),
                paper.getTerm(),
                paper.getRetakeLabel(),
                paper.getTitle(),
                imageCount,
                resourceCount);
    }

    /** Up to five other active subjects in the same category. */
    private List<PublicSubjectCardResponse> relatedCards(ExamSubjectEntity subject) {
        if (subject.getCategorySlug() == null || subject.getCategorySlug().isBlank()) {
            return List.of();
        }
        return subjectRepository
                .findTop6ByCategorySlugAndActiveTrueAndDeletedAtIsNullOrderBySortOrderAsc(
                        subject.getCategorySlug())
                .stream()
                .filter(candidate -> !candidate.getId().equals(subject.getId()))
                .limit(5)
                .map(this::toCard)
                .toList();
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
