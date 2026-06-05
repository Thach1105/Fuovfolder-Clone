package com.fuoverflow.post.application;

import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Idempotent upsert of crawled posts. Re-running a crawl updates the body in place
 * (bumping the edit version) instead of creating duplicate rows.
 */
@Service
public class PostIngestService {
    private final PostRepository postRepository;

    public PostIngestService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Transactional
    public PostEntity upsert(PostIngestCommand command) {
        Instant now = Instant.now();
        PostEntity post = postRepository.findById(command.localId()).orElse(null);
        if (post == null) {
            post = PostEntity.createImported(
                    command.localId(),
                    command.threadId(),
                    command.authorUserId(),
                    command.bodyMd(),
                    command.bodyHtml(),
                    command.createdAt(),
                    now);
        } else {
            boolean changed = !equalsNullable(post.getBodyHtml(), command.bodyHtml());
            post.setThreadId(command.threadId());
            post.setBodyMd(command.bodyMd());
            post.setBodyHtml(command.bodyHtml());
            post.setStatus("visible");
            post.setDeletedAt(null);
            if (changed) {
                post.setEditVersion(post.getEditVersion() + 1);
            }
            post.setUpdatedAt(now);
        }
        post.setImportedAuthorHandle(command.importedAuthorHandle());
        post.setSourceUrl(command.sourceUrl());
        return postRepository.save(post);
    }

    private static boolean equalsNullable(String a, String b) {
        return a == null ? b == null : a.equals(b);
    }
}
