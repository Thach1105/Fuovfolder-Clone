package com.fuoverflow.post.application;

import com.fuoverflow.common.forum.PostBodyFormatter;
import com.fuoverflow.common.forum.ThreadLookup;
import com.fuoverflow.common.notification.NotificationPublisher;
import com.fuoverflow.post.api.dto.CreatePostRequest;
import com.fuoverflow.post.api.dto.PostResponse;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import com.fuoverflow.user.application.UserLookupService;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class PostWriteService {
    private final PostRepository postRepository;
    private final ThreadRepository threadRepository;
    private final ThreadLookup threadLookup;
    private final UserLookupService userLookupService;
    private final NotificationPublisher notificationPublisher;

    public PostWriteService(
            PostRepository postRepository,
            ThreadRepository threadRepository,
            ThreadLookup threadLookup,
            UserLookupService userLookupService,
            NotificationPublisher notificationPublisher) {
        this.postRepository = postRepository;
        this.threadRepository = threadRepository;
        this.threadLookup = threadLookup;
        this.userLookupService = userLookupService;
        this.notificationPublisher = notificationPublisher;
    }

    @Transactional
    public PostResponse reply(UUID threadId, UUID authorUserId, CreatePostRequest request) {
        threadLookup.requireOpen(threadId);
        String authorHandle = userLookupService.findAuthUserById(authorUserId)
                .map(user -> user.displayName() != null ? user.displayName() : user.username())
                .orElse("Thành viên");

        Instant now = Instant.now();
        UUID postId = UUID.randomUUID();
        PostEntity post = PostEntity.createUserPost(
                postId,
                threadId,
                authorUserId,
                authorHandle,
                PostBodyFormatter.toMarkdown(request.body()),
                PostBodyFormatter.toHtml(request.body()),
                now);
        postRepository.save(post);

        ThreadEntity thread = threadRepository.findByIdAndDeletedAtIsNull(threadId).orElseThrow();
        thread.setReplyCount(thread.getReplyCount() + 1);
        thread.setLastPostId(postId);
        thread.setLastPostAt(now);
        thread.setUpdatedAt(now);
        threadRepository.save(thread);

        notificationPublisher.publishThreadReply(
                threadId,
                thread.getTitle(),
                postId,
                authorUserId,
                authorHandle);

        return new PostResponse(
                post.getId(),
                post.getThreadId(),
                post.getImportedAuthorHandle(),
                post.getBodyHtml(),
                post.getStatus(),
                post.getEditVersion(),
                post.getReactionCount(),
                post.getSourceUrl(),
                post.getCreatedAt());
    }
}
