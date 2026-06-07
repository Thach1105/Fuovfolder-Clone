package com.fuoverflow.post.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.forum.PostBodyFormatter;
import com.fuoverflow.common.forum.PostChangeNotifier;
import com.fuoverflow.common.forum.ThreadLookup;
import com.fuoverflow.common.notification.NotificationPublisher;
import com.fuoverflow.material.api.dto.AttachmentResponse;
import com.fuoverflow.material.application.PostAttachmentService;
import com.fuoverflow.post.api.dto.CreatePostRequest;
import com.fuoverflow.post.api.dto.PostResponse;
import com.fuoverflow.post.api.dto.UpdatePostRequest;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import com.fuoverflow.user.application.PermissionResolverService;
import com.fuoverflow.user.application.UserLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PostWriteService {
    private static final List<String> LIVE_STATUSES = List.of("visible", "pending");

    private final PostRepository postRepository;
    private final ThreadRepository threadRepository;
    private final ThreadLookup threadLookup;
    private final UserLookupService userLookupService;
    private final PermissionResolverService permissionResolver;
    private final NotificationPublisher notificationPublisher;
    private final PostChangeNotifier postChangeNotifier;
    private final PostAttachmentService postAttachmentService;

    public PostWriteService(
            PostRepository postRepository,
            ThreadRepository threadRepository,
            ThreadLookup threadLookup,
            UserLookupService userLookupService,
            PermissionResolverService permissionResolver,
            NotificationPublisher notificationPublisher,
            PostChangeNotifier postChangeNotifier,
            PostAttachmentService postAttachmentService) {
        this.postRepository = postRepository;
        this.threadRepository = threadRepository;
        this.threadLookup = threadLookup;
        this.userLookupService = userLookupService;
        this.permissionResolver = permissionResolver;
        this.notificationPublisher = notificationPublisher;
        this.postChangeNotifier = postChangeNotifier;
        this.postAttachmentService = postAttachmentService;
    }

    @Transactional
    public PostResponse reply(UUID threadId, UUID authorUserId, CreatePostRequest request) {
        threadLookup.requireOpen(threadId);
        UUID parentPostId = validateParentPost(threadId, request.parentPostId());
        String authorHandle = resolveAuthorHandle(authorUserId);
        String status = resolveInitialStatus(authorUserId);

        Instant now = Instant.now();
        UUID postId = UUID.randomUUID();
        PostEntity post = PostEntity.createUserPost(
                postId,
                threadId,
                authorUserId,
                parentPostId,
                authorHandle,
                PostBodyFormatter.toMarkdown(request.body()),
                PostBodyFormatter.toHtml(request.body()),
                status,
                now);
        postRepository.save(post);
        postAttachmentService.linkToPost(postId, authorUserId, request.attachmentFileIds());

        if ("visible".equals(status)) {
            updateThreadAfterReply(threadId, postId, now);
        }

        notificationPublisher.publishThreadReply(
                threadId,
                loadThreadTitle(threadId),
                postId,
                authorUserId,
                authorHandle);

        return PostMapper.toResponse(post, null, List.of());
    }

    @Transactional
    public PostResponse update(UUID threadId, UUID postId, UUID actorUserId, UpdatePostRequest request) {
        threadLookup.requireOpen(threadId);
        PostEntity post = requireLivePost(threadId, postId);
        requireAuthorOrModerator(actorUserId, post);

        Instant now = Instant.now();
        post.setBodyMd(PostBodyFormatter.toMarkdown(request.body()));
        post.setBodyHtml(PostBodyFormatter.toHtml(request.body()));
        post.setEditCount(post.getEditCount() + 1);
        post.setEditVersion(post.getEditVersion() + 1);
        post.setLastEditedAt(now);
        post.setUpdatedAt(now);
        postRepository.save(post);
        postChangeNotifier.postUpdated(post.getId(), post.getThreadId());
        return PostMapper.toResponse(post, null, List.of());
    }

    @Transactional
    public void delete(UUID threadId, UUID postId, UUID actorUserId) {
        threadLookup.requireOpen(threadId);
        PostEntity post = requireLivePost(threadId, postId);
        requireAuthorOrModerator(actorUserId, post);

        Instant now = Instant.now();
        post.setDeletedAt(now);
        post.setStatus("deleted");
        post.setUpdatedAt(now);
        postRepository.save(post);
        postChangeNotifier.postDeleted(post.getId(), post.getThreadId());
        recalculateThreadStats(threadId, now);
    }

    @Transactional
    public void hidePost(UUID postId, UUID actorUserId, String reason) {
        PostEntity post = postRepository.findById(postId)
                .filter(entity -> entity.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
        post.setStatus("hidden");
        post.setUpdatedAt(Instant.now());
        postRepository.save(post);
        recalculateThreadStats(post.getThreadId(), Instant.now());
    }

    @Transactional
    public void approvePost(UUID postId, UUID actorUserId) {
        PostEntity post = postRepository.findById(postId)
                .filter(entity -> entity.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
        if (!"pending".equals(post.getStatus())) {
            throw new BadRequestException("POST_NOT_PENDING", "Post is not pending approval");
        }
        Instant now = Instant.now();
        post.setStatus("visible");
        post.setUpdatedAt(now);
        postRepository.save(post);
        updateThreadAfterReply(post.getThreadId(), post.getId(), now);
    }

    @Transactional
    public void rejectPost(UUID postId, UUID actorUserId) {
        PostEntity post = postRepository.findById(postId)
                .filter(entity -> entity.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
        if (!"pending".equals(post.getStatus())) {
            throw new BadRequestException("POST_NOT_PENDING", "Post is not pending approval");
        }
        Instant now = Instant.now();
        post.setStatus("hidden");
        post.setUpdatedAt(now);
        postRepository.save(post);
    }

    private UUID validateParentPost(UUID threadId, UUID parentPostId) {
        if (parentPostId == null) {
            return null;
        }
        PostEntity parent = postRepository.findByIdAndThreadIdAndDeletedAtIsNull(parentPostId, threadId)
                .orElseThrow(() -> new BadRequestException("PARENT_POST_INVALID", "Parent post not found"));
        if (parent.getParentPostId() != null) {
            throw new BadRequestException("NESTED_REPLY_TOO_DEEP", "Cannot reply to a nested reply");
        }
        return parentPostId;
    }

    private String resolveInitialStatus(UUID authorUserId) {
        if (permissionResolver.hasPermission(authorUserId, "forum.post:bypass_moderation")) {
            return "visible";
        }
        return "pending";
    }

    private void updateThreadAfterReply(UUID threadId, UUID postId, Instant now) {
        ThreadEntity thread = threadRepository.findByIdAndDeletedAtIsNull(threadId).orElseThrow();
        thread.setReplyCount(thread.getReplyCount() + 1);
        thread.setLastPostId(postId);
        thread.setLastPostAt(now);
        thread.setUpdatedAt(now);
        threadRepository.save(thread);
    }

    private void recalculateThreadStats(UUID threadId, Instant now) {
        ThreadEntity thread = threadRepository.findByIdAndDeletedAtIsNull(threadId).orElseThrow();
        long visibleCount = postRepository.countByThreadIdAndDeletedAtIsNullAndStatusIn(threadId, List.of("visible"));
        thread.setReplyCount(Math.max(0, (int) visibleCount - 1));
        postRepository.findFirstByThreadIdAndDeletedAtIsNullAndStatusInOrderByCreatedAtDesc(
                        threadId, List.of("visible"))
                .ifPresentOrElse(
                        latest -> {
                            thread.setLastPostId(latest.getId());
                            thread.setLastPostAt(latest.getCreatedAt());
                        },
                        () -> thread.setLastPostId(null));
        thread.setUpdatedAt(now);
        threadRepository.save(thread);
    }

    private PostEntity requireLivePost(UUID threadId, UUID postId) {
        return postRepository.findByIdAndThreadIdAndDeletedAtIsNull(postId, threadId)
                .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
    }

    private void requireAuthorOrModerator(UUID actorUserId, PostEntity post) {
        if (post.getAuthorUserId().equals(actorUserId)) {
            return;
        }
        if (permissionResolver.hasPermission(actorUserId, "forum.post:moderate")) {
            return;
        }
        throw new ForbiddenException("POST_FORBIDDEN", "You cannot modify this post");
    }

    private String resolveAuthorHandle(UUID authorUserId) {
        return userLookupService.findAuthUserById(authorUserId)
                .map(user -> user.displayName() != null ? user.displayName() : user.username())
                .orElse("Thành viên");
    }

    private String loadThreadTitle(UUID threadId) {
        return threadRepository.findByIdAndDeletedAtIsNull(threadId)
                .map(ThreadEntity::getTitle)
                .orElse("Chủ đề");
    }
}
