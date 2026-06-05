package com.fuoverflow.post.application;

import com.fuoverflow.common.forum.PostBodyFormatter;
import com.fuoverflow.common.forum.PostCreator;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

@Service
public class ForumPostCreator implements PostCreator {
    private final PostRepository postRepository;

    public ForumPostCreator(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Override
    @Transactional
    public UUID createInitialPost(UUID threadId, UUID authorUserId, String authorHandle, String body) {
        Instant now = Instant.now();
        UUID postId = UUID.randomUUID();
        String markdown = PostBodyFormatter.toMarkdown(body);
        String html = PostBodyFormatter.toHtml(body);
        PostEntity post = PostEntity.createUserPost(postId, threadId, authorUserId, authorHandle, markdown, html, now);
        postRepository.save(post);
        return postId;
    }
}
