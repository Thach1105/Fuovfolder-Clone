package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.AdminSubjectResponse;
import com.fuoverflow.exam.api.dto.CreateSubjectRequest;
import com.fuoverflow.exam.api.dto.UpdateSubjectRequest;
import com.fuoverflow.exam.config.ExamProperties;
import com.fuoverflow.exam.domain.ExamPaperType;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPaperEntity;
import com.fuoverflow.exam.persistence.ExamPaperRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ExamSubjectAdminService {
    private final ExamSubjectRepository subjectRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamPaperRepository paperRepository;
    private final ExamMediaService mediaService;
    private final ExamMediaUrlResolver urlResolver;
    private final ExamProperties properties;

    public ExamSubjectAdminService(
            ExamSubjectRepository subjectRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamPaperRepository paperRepository,
            ExamMediaService mediaService,
            ExamMediaUrlResolver urlResolver,
            ExamProperties properties) {
        this.subjectRepository = subjectRepository;
        this.feQuestionRepository = feQuestionRepository;
        this.peItemRepository = peItemRepository;
        this.paperRepository = paperRepository;
        this.mediaService = mediaService;
        this.urlResolver = urlResolver;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public List<AdminSubjectResponse> listAll() {
        return subjectRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(this::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminSubjectResponse get(UUID id) {
        return toAdmin(requireSubject(id));
    }

    @Transactional
    public AdminSubjectResponse create(CreateSubjectRequest request) {
        String code = normalizeCode(request.code());
        if (subjectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(code)) {
            throw new ConflictException("EXAM_SUBJECT_CODE_EXISTS", "Exam subject code already exists");
        }
        Instant now = Instant.now();
        String cover = urlResolver.normalizeForStorage(request.coverImageUrl());
        ExamSubjectEntity entity = ExamSubjectEntity.create(
                UUID.randomUUID(),
                code,
                request.title().trim(),
                blankToNull(request.description()),
                cover,
                blankToNull(request.cardColor()),
                blankToNull(request.categorySlug()),
                request.fePreviewImageCount() != null
                        ? request.fePreviewImageCount() : properties.defaultFePreviewImageCountOrDefault(),
                request.active() == null || request.active(),
                request.sortOrder() != null ? request.sortOrder() : 0,
                now);
        ExamSubjectEntity saved = subjectRepository.save(entity);
        mediaService.markLinked(cover);
        return toAdmin(saved);
    }

    @Transactional
    public AdminSubjectResponse update(UUID id, UpdateSubjectRequest request) {
        ExamSubjectEntity entity = requireSubject(id);
        if (request.code() != null) {
            String code = normalizeCode(request.code());
            if (subjectRepository.existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(code, id)) {
                throw new ConflictException("EXAM_SUBJECT_CODE_EXISTS", "Exam subject code already exists");
            }
            entity.setCode(code);
        }
        if (request.title() != null) {
            entity.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            entity.setDescription(blankToNull(request.description()));
        }
        if (request.coverImageUrl() != null) {
            String oldCover = entity.getCoverImageUrl();
            String newCover = urlResolver.normalizeForStorage(request.coverImageUrl());
            if (oldCover != null && !oldCover.equals(newCover)) {
                mediaService.unlinkStoredReference(oldCover);
            }
            entity.setCoverImageUrl(newCover);
            mediaService.markLinked(newCover);
        }
        if (request.cardColor() != null) {
            entity.setCardColor(blankToNull(request.cardColor()));
        }
        if (request.categorySlug() != null) {
            entity.setCategorySlug(blankToNull(request.categorySlug()));
        }
        if (request.fePreviewImageCount() != null) {
            entity.setFePreviewImageCount(request.fePreviewImageCount());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        entity.setUpdatedAt(Instant.now());
        return toAdmin(subjectRepository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        ExamSubjectEntity entity = requireSubject(id);
        mediaService.deleteStoredReference(entity.getCoverImageUrl());
        Instant now = Instant.now();
        entity.setDeletedAt(now);
        entity.setActive(false);
        entity.setUpdatedAt(now);
        subjectRepository.save(entity);
    }

    ExamSubjectEntity requireSubject(UUID id) {
        return subjectRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("EXAM_SUBJECT_NOT_FOUND", "Exam subject not found"));
    }

    private AdminSubjectResponse toAdmin(ExamSubjectEntity e) {
        long fePaperCount = paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(
                e.getId(), ExamPaperType.FE.dbValue());
        long pePaperCount = paperRepository.countBySubjectIdAndPaperTypeAndDeletedAtIsNull(
                e.getId(), ExamPaperType.PE.dbValue());
        AdminSubjectResponse.LatestPaperSummary latestPaper = paperRepository
                .findFirstBySubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(e.getId())
                .map(ExamSubjectAdminService::toLatestPaperSummary)
                .orElse(null);
        return new AdminSubjectResponse(
                e.getId(),
                e.getCode(),
                e.getTitle(),
                e.getDescription(),
                urlResolver.plain(e.getCoverImageUrl()),
                e.getCardColor(),
                e.getCategorySlug(),
                e.getFePreviewImageCount(),
                e.getViewCount(),
                e.isActive(),
                e.getSortOrder(),
                (int) feQuestionRepository.countBySubjectIdAndDeletedAtIsNull(e.getId()),
                (int) peItemRepository.countBySubjectIdAndDeletedAtIsNull(e.getId()),
                (int) fePaperCount,
                (int) pePaperCount,
                latestPaper,
                e.getCreatedAt(),
                e.getUpdatedAt());
    }

    private static AdminSubjectResponse.LatestPaperSummary toLatestPaperSummary(ExamPaperEntity paper) {
        return new AdminSubjectResponse.LatestPaperSummary(
                paper.getExamCode(), paper.getPaperType(), paper.getStatus(), paper.getTerm(), paper.getCreatedAt());
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
