package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.forum.PostBodyFormatter;
import com.fuoverflow.exam.api.dto.AdminCommentPageResponse;
import com.fuoverflow.exam.api.dto.AdminCommentResponse;
import com.fuoverflow.exam.api.dto.CreateCommentRequest;
import com.fuoverflow.exam.api.dto.ExamCommentResponse;
import com.fuoverflow.exam.api.dto.UpdateCommentRequest;
import com.fuoverflow.exam.persistence.ExamCommentEntity;
import com.fuoverflow.exam.persistence.ExamCommentRepository;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamSubjectEntity;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class ExamCommentService {
    static final String SUBJECT_FE = "fe_question";
    static final String SUBJECT_PE = "pe_item";

    private final ExamCommentRepository commentRepository;
    private final ExamFeQuestionRepository feQuestionRepository;
    private final ExamPeItemRepository peItemRepository;
    private final ExamSubjectRepository subjectRepository;
    private final ExamAccessGuard accessGuard;
    private final UserRepository userRepository;
    private final UploadService uploadService;

    public ExamCommentService(
            ExamCommentRepository commentRepository,
            ExamFeQuestionRepository feQuestionRepository,
            ExamPeItemRepository peItemRepository,
            ExamSubjectRepository subjectRepository,
            ExamAccessGuard accessGuard,
            UserRepository userRepository,
            UploadService uploadService) {
        this.commentRepository = commentRepository;
        this.feQuestionRepository = feQuestionRepository;
        this.peItemRepository = peItemRepository;
        this.subjectRepository = subjectRepository;
        this.accessGuard = accessGuard;
        this.userRepository = userRepository;
        this.uploadService = uploadService;
    }

    @Transactional(readOnly = true)
    public List<ExamCommentResponse> list(String subjectType, UUID subjectId, UUID userId) {
        accessGuard.requireActiveMembership(userId);
        validateSubjectExists(subjectType, subjectId);
        List<ExamCommentEntity> comments =
                commentRepository.findBySubjectTypeAndSubjectIdAndDeletedAtIsNullOrderByCreatedAtAsc(
                        subjectType, subjectId);
        Map<UUID, UserEntity> authors = loadAuthors(comments);
        return comments.stream().map(c -> toResponse(c, authors, userId)).toList();
    }

    @Transactional
    public ExamCommentResponse create(String subjectType, UUID subjectId, UUID userId, CreateCommentRequest request) {
        accessGuard.requireActiveMembership(userId);
        UUID examSubjectId = validateSubjectExists(subjectType, subjectId);

        if (request.parentCommentId() != null) {
            ExamCommentEntity parent = commentRepository.findByIdAndDeletedAtIsNull(request.parentCommentId())
                    .orElseThrow(() -> new NotFoundException("EXAM_COMMENT_NOT_FOUND", "Parent comment not found"));
            if (!parent.getSubjectType().equals(subjectType) || !parent.getSubjectId().equals(subjectId)) {
                throw new NotFoundException("EXAM_COMMENT_NOT_FOUND", "Parent comment not found");
            }
        }

        String bodyMd = PostBodyFormatter.toMarkdown(request.body());
        String bodyHtml = PostBodyFormatter.toHtml(request.body());
        Instant now = Instant.now();
        ExamCommentEntity entity = ExamCommentEntity.create(
                UUID.randomUUID(), subjectType, subjectId, examSubjectId,
                userId, request.parentCommentId(), bodyMd, bodyHtml, now);
        commentRepository.save(entity);
        return toResponse(entity, loadAuthors(List.of(entity)), userId);
    }

    @Transactional
    public ExamCommentResponse update(UUID commentId, UUID userId, UpdateCommentRequest request) {
        accessGuard.requireActiveMembership(userId);
        ExamCommentEntity entity = requireComment(commentId);
        if (!entity.getAuthorUserId().equals(userId)) {
            throw new ForbiddenException("EXAM_COMMENT_FORBIDDEN", "You can only edit your own comment");
        }
        entity.setBodyMd(PostBodyFormatter.toMarkdown(request.body()));
        entity.setBodyHtml(PostBodyFormatter.toHtml(request.body()));
        entity.setUpdatedAt(Instant.now());
        commentRepository.save(entity);
        return toResponse(entity, loadAuthors(List.of(entity)), userId);
    }

    @Transactional
    public void delete(UUID commentId, UUID userId) {
        accessGuard.requireActiveMembership(userId);
        ExamCommentEntity entity = requireComment(commentId);
        if (!entity.getAuthorUserId().equals(userId)) {
            throw new ForbiddenException("EXAM_COMMENT_FORBIDDEN", "You can only delete your own comment");
        }
        softDelete(entity);
    }

    /** Admin/moderator removal — bypasses author check. */
    @Transactional
    public void adminDelete(UUID commentId) {
        softDelete(requireComment(commentId));
    }

    /**
     * Moderation listing (newest-first, paged). No membership gate — controller enforces
     * {@code exam.comment.admin:delete}. Optional filters by subject type and exam subject.
     */
    @Transactional(readOnly = true)
    public AdminCommentPageResponse adminList(String subjectType, UUID examSubjectId, int page, int size) {
        int safeSize = size <= 0 || size > 100 ? 20 : size;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        String type = normalizeSubjectType(subjectType);

        Page<ExamCommentEntity> result;
        if (type != null && examSubjectId != null) {
            result = commentRepository
                    .findBySubjectTypeAndExamSubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(type, examSubjectId, pageable);
        } else if (type != null) {
            result = commentRepository.findBySubjectTypeAndDeletedAtIsNullOrderByCreatedAtDesc(type, pageable);
        } else if (examSubjectId != null) {
            result = commentRepository.findByExamSubjectIdAndDeletedAtIsNullOrderByCreatedAtDesc(examSubjectId, pageable);
        } else {
            result = commentRepository.findByDeletedAtIsNullOrderByCreatedAtDesc(pageable);
        }

        List<ExamCommentEntity> comments = result.getContent();
        Map<UUID, UserEntity> authors = loadAuthors(comments);
        Map<UUID, String> subjectCodes = loadSubjectCodes(comments);
        List<AdminCommentResponse> items = comments.stream()
                .map(c -> toAdminResponse(c, authors, subjectCodes))
                .toList();
        return new AdminCommentPageResponse(
                items, result.getNumber(), result.getSize(),
                result.getTotalElements(), result.getTotalPages());
    }

    // --- internals ------------------------------------------------------------

    private void softDelete(ExamCommentEntity entity) {
        Instant now = Instant.now();
        entity.setDeletedAt(now);
        entity.setStatus("hidden");
        entity.setUpdatedAt(now);
        commentRepository.save(entity);
    }

    private UUID validateSubjectExists(String subjectType, UUID subjectId) {
        return switch (subjectType) {
            case SUBJECT_FE -> feQuestionRepository.findByIdAndDeletedAtIsNull(subjectId)
                    .map(q -> q.getSubjectId())
                    .orElseThrow(() -> new NotFoundException("EXAM_FE_QUESTION_NOT_FOUND", "Question not found"));
            case SUBJECT_PE -> peItemRepository.findByIdAndDeletedAtIsNull(subjectId)
                    .map(p -> p.getSubjectId())
                    .orElseThrow(() -> new NotFoundException("EXAM_PE_ITEM_NOT_FOUND", "PE item not found"));
            default -> throw new NotFoundException("EXAM_SUBJECT_TYPE_INVALID", "Unknown subject type");
        };
    }

    private ExamCommentEntity requireComment(UUID commentId) {
        return commentRepository.findByIdAndDeletedAtIsNull(commentId)
                .orElseThrow(() -> new NotFoundException("EXAM_COMMENT_NOT_FOUND", "Comment not found"));
    }

    private static String normalizeSubjectType(String subjectType) {
        if (subjectType == null || subjectType.isBlank()) {
            return null;
        }
        String value = subjectType.trim();
        if (!SUBJECT_FE.equals(value) && !SUBJECT_PE.equals(value)) {
            throw new NotFoundException("EXAM_SUBJECT_TYPE_INVALID", "Unknown subject type");
        }
        return value;
    }

    private Map<UUID, String> loadSubjectCodes(List<ExamCommentEntity> comments) {
        Set<UUID> ids = comments.stream().map(ExamCommentEntity::getExamSubjectId).collect(Collectors.toSet());
        Map<UUID, String> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (ExamSubjectEntity subject : subjectRepository.findAllById(ids)) {
            map.put(subject.getId(), subject.getCode());
        }
        return map;
    }

    private AdminCommentResponse toAdminResponse(
            ExamCommentEntity c, Map<UUID, UserEntity> authors, Map<UUID, String> subjectCodes) {
        UserEntity author = authors.get(c.getAuthorUserId());
        return new AdminCommentResponse(
                c.getId(),
                c.getSubjectType(),
                c.getSubjectId(),
                c.getExamSubjectId(),
                subjectCodes.get(c.getExamSubjectId()),
                c.getAuthorUserId(),
                author != null ? author.getUsername() : null,
                author != null ? author.getDisplayName() : null,
                c.getParentCommentId(),
                c.getBodyHtml(),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }

    private Map<UUID, UserEntity> loadAuthors(List<ExamCommentEntity> comments) {
        Set<UUID> ids = comments.stream().map(ExamCommentEntity::getAuthorUserId).collect(Collectors.toSet());
        Map<UUID, UserEntity> map = new HashMap<>();
        if (ids.isEmpty()) {
            return map;
        }
        for (UserEntity user : userRepository.findAllById(ids)) {
            map.put(user.getId(), user);
        }
        return map;
    }

    private ExamCommentResponse toResponse(ExamCommentEntity c, Map<UUID, UserEntity> authors, UUID viewerId) {
        UserEntity author = authors.get(c.getAuthorUserId());
        return new ExamCommentResponse(
                c.getId(),
                c.getSubjectType(),
                c.getSubjectId(),
                c.getAuthorUserId(),
                author != null ? author.getUsername() : null,
                author != null ? author.getDisplayName() : null,
                author != null ? uploadService.resolvePublicUrl(author.getAvatarUrl()) : null,
                c.getParentCommentId(),
                c.getBodyHtml(),
                c.getAuthorUserId().equals(viewerId),
                c.getCreatedAt(),
                c.getUpdatedAt());
    }
}
