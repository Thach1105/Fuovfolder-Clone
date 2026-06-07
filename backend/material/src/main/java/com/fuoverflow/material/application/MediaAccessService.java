package com.fuoverflow.material.application;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.forum.ForumContentAccessChecker;
import com.fuoverflow.common.storage.ObjectStorage;
import com.fuoverflow.material.domain.UploadPurpose;
import com.fuoverflow.material.persistence.PostAttachmentRepository;
import com.fuoverflow.material.persistence.UploadedFileEntity;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.InputStream;
import java.util.UUID;

@Service
public class MediaAccessService {
    private final UploadService uploadService;
    private final ObjectStorage objectStorage;
    private final PostAttachmentRepository postAttachmentRepository;
    private final ForumContentAccessChecker forumContentAccessChecker;

    public MediaAccessService(
            UploadService uploadService,
            ObjectStorage objectStorage,
            PostAttachmentRepository postAttachmentRepository,
            ForumContentAccessChecker forumContentAccessChecker) {
        this.uploadService = uploadService;
        this.objectStorage = objectStorage;
        this.postAttachmentRepository = postAttachmentRepository;
        this.forumContentAccessChecker = forumContentAccessChecker;
    }

    @Transactional(readOnly = true)
    public DownloadResource openDownload(UUID fileId, UUID viewerUserId) {
        UploadedFileEntity file = uploadService.requireActiveFile(fileId);
        authorizeDownload(file, viewerUserId);
        InputStream stream = objectStorage.openStream(file.getStoragePath());
        return new DownloadResource(stream, file.getOriginalFilename(), file.getMimeType(), file.getSizeBytes());
    }

    private void authorizeDownload(UploadedFileEntity file, UUID viewerUserId) {
        if (UploadPurpose.FORUM_ATTACHMENT.slug().equals(file.getPurpose())) {
            if (file.getLinkedAt() == null) {
                throw new NotFoundException("FILE_NOT_FOUND", "File not found");
            }
            UUID postId = postAttachmentRepository.findByUploadedFileId(file.getId())
                    .map(attachment -> attachment.getPostId())
                    .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "File not found"));
            UUID threadId = forumContentAccessChecker.findThreadIdForPost(postId)
                    .orElseThrow(() -> new NotFoundException("FILE_NOT_FOUND", "File not found"));
            if (!forumContentAccessChecker.canReadThread(viewerUserId, threadId)) {
                throw new ForbiddenException("FILE_FORBIDDEN", "You cannot access this file");
            }
            return;
        }
        if (viewerUserId == null) {
            throw new ForbiddenException("FILE_FORBIDDEN", "Authentication required");
        }
        if (!viewerUserId.equals(file.getOwnerUserId())) {
            throw new ForbiddenException("FILE_FORBIDDEN", "You cannot access this file");
        }
    }

    public record DownloadResource(InputStream stream, String filename, String mimeType, long sizeBytes) {
    }
}
