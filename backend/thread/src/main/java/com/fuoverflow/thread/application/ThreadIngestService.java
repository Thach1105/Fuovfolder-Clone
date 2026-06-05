package com.fuoverflow.thread.application;

import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Idempotent upsert of crawled threads. Called by the forum sync pipeline; safe to
 * re-run because the local id is stable per external source id.
 */
@Service
public class ThreadIngestService {
    private final ThreadRepository threadRepository;

    public ThreadIngestService(ThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    @Transactional
    public ThreadEntity upsert(ThreadIngestCommand command) {
        Instant now = Instant.now();
        ThreadEntity thread = threadRepository.findById(command.localId()).orElse(null);
        if (thread == null) {
            thread = ThreadEntity.createImported(
                    command.localId(),
                    command.forumId(),
                    command.categoryId(),
                    command.authorUserId(),
                    command.title(),
                    command.slug(),
                    command.lastPostAt(),
                    now);
        } else {
            thread.setForumId(command.forumId());
            thread.setCategoryId(command.categoryId());
            thread.setTitle(command.title());
            thread.setSlug(command.slug());
            thread.setDeletedAt(null);
        }
        thread.setImportedAuthorHandle(command.importedAuthorHandle());
        thread.setSourceUrl(command.sourceUrl());
        if (command.lastPostAt() != null) {
            thread.setLastPostAt(command.lastPostAt());
        }
        if (command.replyCount() != null) {
            thread.setReplyCount(Math.max(0, command.replyCount()));
        }
        if (command.viewCount() != null) {
            thread.setViewCount(Math.max(0L, command.viewCount()));
        }
        return threadRepository.save(thread);
    }

    /**
     * Updates derived activity fields after posts for the thread have been ingested.
     */
    @Transactional
    public void applyAggregates(UUID threadId, UUID lastPostId, Instant lastPostAt, int replyCount) {
        threadRepository.findById(threadId).ifPresent(thread -> {
            if (lastPostId != null) {
                thread.setLastPostId(lastPostId);
            }
            if (lastPostAt != null) {
                thread.setLastPostAt(lastPostAt);
            }
            thread.setReplyCount(Math.max(0, replyCount));
            threadRepository.save(thread);
        });
    }
}
