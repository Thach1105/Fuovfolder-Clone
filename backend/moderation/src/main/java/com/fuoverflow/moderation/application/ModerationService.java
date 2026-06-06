package com.fuoverflow.moderation.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.moderation.api.dto.CreateFlagRequest;
import com.fuoverflow.moderation.api.dto.FlagPageResponse;
import com.fuoverflow.moderation.api.dto.FlagResponse;
import com.fuoverflow.moderation.api.dto.ResolveFlagRequest;
import com.fuoverflow.moderation.persistence.ContentFlagEntity;
import com.fuoverflow.moderation.persistence.ContentFlagRepository;
import com.fuoverflow.moderation.persistence.ModerationActionEntity;
import com.fuoverflow.moderation.persistence.ModerationActionRepository;
import com.fuoverflow.post.application.PostWriteService;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ModerationService {
    private static final Set<String> ALLOWED_TARGETS = Set.of("thread", "post");
    private static final Set<String> RESOLVE_ACTIONS = Set.of("hide", "reject", "delete", "lock_thread");

    private final ContentFlagRepository flagRepository;
    private final ModerationActionRepository actionRepository;
    private final PostWriteService postWriteService;
    private final PostRepository postRepository;
    private final ThreadRepository threadRepository;

    public ModerationService(
            ContentFlagRepository flagRepository,
            ModerationActionRepository actionRepository,
            PostWriteService postWriteService,
            PostRepository postRepository,
            ThreadRepository threadRepository) {
        this.flagRepository = flagRepository;
        this.actionRepository = actionRepository;
        this.postWriteService = postWriteService;
        this.postRepository = postRepository;
        this.threadRepository = threadRepository;
    }

    @Transactional
    public FlagResponse createFlag(UUID reporterUserId, CreateFlagRequest request) {
        String targetType = normalizeTargetType(request.targetType());
        validateTargetExists(targetType, request.targetId());
        flagRepository.findByReporterUserIdAndTargetTypeAndTargetIdAndStatus(
                        reporterUserId, targetType, request.targetId(), "open")
                .ifPresent(existing -> {
                    throw new BadRequestException("FLAG_ALREADY_OPEN", "You already reported this content");
                });

        Instant now = Instant.now();
        ContentFlagEntity flag = ContentFlagEntity.create(
                reporterUserId,
                targetType,
                request.targetId(),
                request.reason().trim(),
                request.note(),
                now);
        flagRepository.save(flag);
        return toResponse(flag);
    }

    @Transactional(readOnly = true)
    public FlagPageResponse listFlags(String status, int page, int size) {
        String normalizedStatus = status == null || status.isBlank() ? "open" : status.trim();
        Pageable pageable = PageRequest.of(Math.max(page, 0), Math.max(size, 1));
        Page<ContentFlagEntity> result = flagRepository.findByStatusOrderByCreatedAtDesc(normalizedStatus, pageable);
        return new FlagPageResponse(
                result.getContent().stream().map(ModerationService::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional
    public FlagResponse resolveFlag(UUID flagId, UUID actorUserId, ResolveFlagRequest request) {
        ContentFlagEntity flag = flagRepository.findById(flagId)
                .orElseThrow(() -> new NotFoundException("FLAG_NOT_FOUND", "Flag not found"));
        if (!"open".equals(flag.getStatus()) && !"reviewing".equals(flag.getStatus())) {
            throw new BadRequestException("FLAG_ALREADY_RESOLVED", "Flag is already resolved");
        }

        String action = request.action().trim().toLowerCase(Locale.ROOT);
        if (!RESOLVE_ACTIONS.contains(action)) {
            throw new BadRequestException("FLAG_ACTION_INVALID", "Unsupported moderation action");
        }

        applyAction(actorUserId, flag.getTargetType(), flag.getTargetId(), action, request.reason());
        Instant now = Instant.now();
        flag.setStatus("reject".equals(action) ? "rejected" : "resolved");
        flag.setResolverUserId(actorUserId);
        flag.setResolvedAt(now);
        flagRepository.save(flag);
        recordAction(actorUserId, action, flag.getTargetType(), flag.getTargetId(), request.reason(), now);
        return toResponse(flag);
    }

    private void applyAction(UUID actorUserId, String targetType, UUID targetId, String action, String reason) {
        switch (action) {
            case "hide" -> {
                if (!"post".equals(targetType)) {
                    throw new BadRequestException("TARGET_INVALID", "Hide action applies to posts only");
                }
                postWriteService.hidePost(targetId, actorUserId, reason);
            }
            case "delete" -> {
                if (!"post".equals(targetType)) {
                    throw new BadRequestException("TARGET_INVALID", "Delete action applies to posts only");
                }
                PostEntity post = postRepository.findById(targetId)
                        .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
                postWriteService.delete(post.getThreadId(), targetId, actorUserId);
            }
            case "lock_thread" -> {
                if (!"thread".equals(targetType)) {
                    throw new BadRequestException("TARGET_INVALID", "Lock action applies to threads only");
                }
                ThreadEntity thread = threadRepository.findByIdAndDeletedAtIsNull(targetId)
                        .orElseThrow(() -> new NotFoundException("THREAD_NOT_FOUND", "Thread not found"));
                thread.setLockedAt(Instant.now());
                thread.setStatus("locked");
                thread.setUpdatedAt(Instant.now());
                threadRepository.save(thread);
            }
            case "reject" -> {
                // no target mutation; mark flag rejected only
            }
            default -> throw new BadRequestException("FLAG_ACTION_INVALID", "Unsupported moderation action");
        }
    }

    private void recordAction(
            UUID actorUserId,
            String action,
            String targetType,
            UUID targetId,
            String reason,
            Instant now) {
        actionRepository.save(ModerationActionEntity.create(
                actorUserId, action, targetType, targetId, reason, null, now));
    }

    private void validateTargetExists(String targetType, UUID targetId) {
        if ("post".equals(targetType)) {
            postRepository.findById(targetId)
                    .filter(post -> post.getDeletedAt() == null)
                    .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
            return;
        }
        threadRepository.findByIdAndDeletedAtIsNull(targetId)
                .orElseThrow(() -> new NotFoundException("THREAD_NOT_FOUND", "Thread not found"));
    }

    private String normalizeTargetType(String targetType) {
        String normalized = targetType.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TARGETS.contains(normalized)) {
            throw new BadRequestException("TARGET_TYPE_INVALID", "Target type is not supported");
        }
        return normalized;
    }

    private static FlagResponse toResponse(ContentFlagEntity flag) {
        return new FlagResponse(
                flag.getId(),
                flag.getReporterUserId(),
                flag.getTargetType(),
                flag.getTargetId(),
                flag.getReason(),
                flag.getNote(),
                flag.getStatus(),
                flag.getCreatedAt());
    }
}
