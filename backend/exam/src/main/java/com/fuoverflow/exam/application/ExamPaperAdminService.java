package com.fuoverflow.exam.application;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminPaperResponse;
import com.fuoverflow.exam.api.dto.AdminWebhookEventResponse;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemEntity;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamPeResourceEntity;
import com.fuoverflow.exam.persistence.ExamPeResourceRepository;
import com.fuoverflow.exam.persistence.ExamWebhookEventEntity;
import com.fuoverflow.exam.persistence.ExamWebhookEventRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Admin review surface for the paper bank: list what arrived, publish it, or throw it away.
 *
 * <p>Publishing is gated on the paper actually having content — a webhook that failed halfway
 * leaves a draft behind, and letting that reach members would show them an empty paper.
 */
@Service
public class ExamPaperAdminService {

    private final ExamPaperRepository paperRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPeResourceRepository peResourceRepository;
    private final ExamWebhookEventRepository webhookEventRepository;
    private final ExamMediaService mediaService;
    private final ObjectMapper objectMapper;

    public ExamPaperAdminService(
            ExamPaperRepository paperRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPeResourceRepository peResourceRepository,
            ExamWebhookEventRepository webhookEventRepository,
            ExamMediaService mediaService,
            ObjectMapper objectMapper) {
        this.paperRepository = paperRepository;
        this.feQuestionRepository = feQuestionRepository;
        this.peItemRepository = peItemRepository;
        this.peResourceRepository = peResourceRepository;
        this.webhookEventRepository = webhookEventRepository;
        this.mediaService = mediaService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<AdminPaperResponse> list(UUID subjectId, String status) {
        List<ExamPaperEntity> papers;
        if (subjectId != null) {
            papers = paperRepository
                    .findBySubjectIdAndDeletedAtIsNullOrderBySortOrderAscCreatedAtDesc(subjectId);
            if (status != null && !status.isBlank()) {
                papers = papers.stream().filter(p -> status.equals(p.getStatus())).toList();
            }
        } else if (status != null && !status.isBlank()) {
            papers = paperRepository.findByStatusAndDeletedAtIsNullOrderByCreatedAtDesc(status);
        } else {
            papers = paperRepository.findByDeletedAtIsNullOrderByCreatedAtDesc();
        }
        return papers.stream().map(this::toAdmin).toList();
    }

    @Transactional(readOnly = true)
    public AdminPaperResponse get(UUID paperId) {
        return toAdmin(requirePaper(paperId));
    }

    @Transactional
    public AdminPaperResponse publish(UUID paperId) {
        ExamPaperEntity paper = requirePaper(paperId);
        if (paper.isPublished()) {
            return toAdmin(paper);
        }
        requireContent(paper);
        paper.publish(Instant.now());
        paperRepository.save(paper);
        return toAdmin(paper);
    }

    @Transactional
    public void delete(UUID paperId) {
        ExamPaperEntity paper = requirePaper(paperId);
        Instant now = Instant.now();

        for (ExamFeQuestionEntity question
                : feQuestionRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paperId)) {
            mediaService.deletePairedAll(
                    ExamJsonUtil.deserialize(objectMapper, question.getQuestionImageUrls()),
                    ExamJsonUtil.deserialize(objectMapper, question.getQuestionBlurUrls()));
            question.setDeletedAt(now);
            question.setUpdatedAt(now);
            feQuestionRepository.save(question);
        }

        for (ExamPeItemEntity item
                : peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paperId)) {
            mediaService.deleteStoredReferences(
                    ExamJsonUtil.deserialize(objectMapper, item.getExamImageUrls()));
            for (ExamPeResourceEntity resource
                    : peResourceRepository.findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId())) {
                mediaService.deleteStoredReference(resource.getObjectKey());
                resource.setDeletedAt(now);
                peResourceRepository.save(resource);
            }
            item.setDeletedAt(now);
            item.setUpdatedAt(now);
            peItemRepository.save(item);
        }

        paper.setDeletedAt(now);
        paper.setUpdatedAt(now);
        paperRepository.save(paper);
    }

    @Transactional(readOnly = true)
    public AdminWebhookEventResponse getWebhookEvent(UUID receiptId) {
        ExamWebhookEventEntity event = webhookEventRepository.findById(receiptId)
                .orElseThrow(() -> new NotFoundException(
                        "EXAM_WEBHOOK_EVENT_NOT_FOUND", "Webhook receipt not found"));
        return new AdminWebhookEventResponse(
                event.getId(),
                event.getClientId(),
                event.getEventId(),
                event.getStatus(),
                event.getAttemptCount(),
                event.getPaperId(),
                event.getErrorCode(),
                event.getErrorMessage(),
                event.getCreatedAt(),
                event.getProcessedAt());
    }

    // --- internals ------------------------------------------------------------

    private void requireContent(ExamPaperEntity paper) {
        if (paper.paperTypeEnum() == ExamPaperType.FE) {
            if (feQuestionRepository.countByPaperIdAndDeletedAtIsNull(paper.getId()) == 0) {
                throw emptyPaper();
            }
            return;
        }
        for (ExamPeItemEntity item
                : peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paper.getId())) {
            boolean hasImages = !ExamJsonUtil
                    .deserialize(objectMapper, item.getExamImageUrls()).isEmpty();
            boolean hasResources = !peResourceRepository
                    .findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()).isEmpty();
            if (hasImages || hasResources) {
                return;
            }
        }
        throw emptyPaper();
    }

    private static BadRequestException emptyPaper() {
        return new BadRequestException("EXAM_PAPER_EMPTY",
                "Đề chưa có nội dung nên không thể phát hành.");
    }

    private ExamPaperEntity requirePaper(UUID paperId) {
        return paperRepository.findByIdAndDeletedAtIsNull(paperId)
                .orElseThrow(() -> new NotFoundException("EXAM_PAPER_NOT_FOUND", "Exam paper not found"));
    }

    private AdminPaperResponse toAdmin(ExamPaperEntity paper) {
        int questionCount = 0;
        int resourceCount = 0;
        if (paper.paperTypeEnum() == ExamPaperType.FE) {
            questionCount = (int) feQuestionRepository.countByPaperIdAndDeletedAtIsNull(paper.getId());
        } else {
            for (ExamPeItemEntity item
                    : peItemRepository.findByPaperIdAndDeletedAtIsNullOrderBySortOrderAsc(paper.getId())) {
                questionCount += ExamJsonUtil
                        .deserialize(objectMapper, item.getExamImageUrls()).size();
                resourceCount += peResourceRepository
                        .findByPeItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()).size();
            }
        }
        return new AdminPaperResponse(
                paper.getId(),
                paper.getSubjectId(),
                paper.getPaperType(),
                paper.getExamCode(),
                paper.getTerm(),
                paper.getRetakeLabel(),
                paper.getTitle(),
                paper.getStatus(),
                paper.getIngestSource(),
                questionCount,
                resourceCount,
                paper.getPublishedAt(),
                paper.getCreatedAt());
    }
}
