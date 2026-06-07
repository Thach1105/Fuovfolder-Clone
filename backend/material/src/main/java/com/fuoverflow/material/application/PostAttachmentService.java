package com.fuoverflow.material.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.material.api.dto.AttachmentResponse;
import com.fuoverflow.material.domain.UploadPurpose;
import com.fuoverflow.material.persistence.PostAttachmentEntity;
import com.fuoverflow.material.persistence.PostAttachmentRepository;
import com.fuoverflow.material.persistence.UploadedFileEntity;
import com.fuoverflow.material.persistence.UploadedFileRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class PostAttachmentService {
    private static final int MAX_ATTACHMENTS = 5;

    private final PostAttachmentRepository postAttachmentRepository;
    private final UploadedFileRepository uploadedFileRepository;
    private final UploadService uploadService;

    public PostAttachmentService(
            PostAttachmentRepository postAttachmentRepository,
            UploadedFileRepository uploadedFileRepository,
            UploadService uploadService) {
        this.postAttachmentRepository = postAttachmentRepository;
        this.uploadedFileRepository = uploadedFileRepository;
        this.uploadService = uploadService;
    }

    @Transactional
    public void linkToPost(UUID postId, UUID ownerUserId, List<UUID> attachmentFileIds) {
        if (attachmentFileIds == null || attachmentFileIds.isEmpty()) {
            return;
        }
        if (attachmentFileIds.size() > MAX_ATTACHMENTS) {
            throw new BadRequestException("TOO_MANY_ATTACHMENTS", "Maximum " + MAX_ATTACHMENTS + " attachments allowed");
        }
        Instant now = Instant.now();
        int order = 0;
        for (UUID fileId : attachmentFileIds) {
            UploadedFileEntity file = uploadService.requireOwnedStagingFile(fileId, ownerUserId, UploadPurpose.FORUM_ATTACHMENT);
            file.setLinkedAt(now);
            uploadedFileRepository.save(file);
            postAttachmentRepository.save(PostAttachmentEntity.create(
                    UUID.randomUUID(), postId, file.getId(), order++, now));
        }
    }

    @Transactional(readOnly = true)
    public List<AttachmentResponse> listForPost(UUID postId) {
        return postAttachmentRepository.findByPostIdOrderBySortOrderAsc(postId).stream()
                .map(this::toAttachmentResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public Map<UUID, List<AttachmentResponse>> listForPosts(Collection<UUID> postIds) {
        if (postIds == null || postIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, List<AttachmentResponse>> grouped = new HashMap<>();
        for (PostAttachmentEntity attachment : postAttachmentRepository.findByPostIdInOrderByPostIdAscSortOrderAsc(postIds)) {
            grouped.computeIfAbsent(attachment.getPostId(), ignored -> new ArrayList<>())
                    .add(toAttachmentResponse(attachment));
        }
        return grouped;
    }

    private AttachmentResponse toAttachmentResponse(PostAttachmentEntity attachment) {
        UploadedFileEntity file = uploadedFileRepository.findById(attachment.getUploadedFileId())
                .orElseThrow(() -> new BadRequestException("FILE_NOT_FOUND", "Attachment file not found"));
        return new AttachmentResponse(
                file.getId(),
                file.getOriginalFilename(),
                file.getMimeType(),
                file.getSizeBytes());
    }
}
