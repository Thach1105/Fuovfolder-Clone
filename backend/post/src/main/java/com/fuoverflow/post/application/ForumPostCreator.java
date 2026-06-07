package com.fuoverflow.post.application;

import com.fuoverflow.common.forum.PostBodyFormatter;
import com.fuoverflow.common.forum.PostCreator;
import com.fuoverflow.material.application.PostAttachmentService;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import com.fuoverflow.user.application.PermissionResolverService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class ForumPostCreator implements PostCreator {
    private final PostRepository postRepository;
    private final PermissionResolverService permissionResolver;
    private final PostAttachmentService postAttachmentService;

    public ForumPostCreator(
            PostRepository postRepository,
            PermissionResolverService permissionResolver,
            PostAttachmentService postAttachmentService) {
        this.postRepository = postRepository;
        this.permissionResolver = permissionResolver;
        this.postAttachmentService = postAttachmentService;
    }

    @Override
    @Transactional
    public UUID createInitialPost(
            UUID threadId,
            UUID authorUserId,
            String authorHandle,
            String body,
            List<UUID> attachmentFileIds) {
        Instant now = Instant.now();
        UUID postId = UUID.randomUUID();
        String markdown = PostBodyFormatter.toMarkdown(body);
        String html = PostBodyFormatter.toHtml(body);
        String status = permissionResolver.hasPermission(authorUserId, "forum.post:bypass_moderation")
                ? "visible"
                : "pending";
        PostEntity post = PostEntity.createUserPost(
                postId, threadId, authorUserId, null, authorHandle, markdown, html, status, now);
        postRepository.save(post);
        postAttachmentService.linkToPost(postId, authorUserId, attachmentFileIds);
        return postId;
    }
}
