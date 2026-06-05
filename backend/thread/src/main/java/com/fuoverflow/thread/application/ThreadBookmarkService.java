package com.fuoverflow.thread.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.thread.api.dto.ThreadBookmarkStatusResponse;
import com.fuoverflow.thread.api.dto.ThreadPageResponse;
import com.fuoverflow.thread.persistence.ThreadBookmarkEntity;
import com.fuoverflow.thread.persistence.ThreadBookmarkRepository;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;

@Service
public class ThreadBookmarkService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ThreadBookmarkRepository bookmarkRepository;
    private final ThreadRepository threadRepository;

    public ThreadBookmarkService(ThreadBookmarkRepository bookmarkRepository, ThreadRepository threadRepository) {
        this.bookmarkRepository = bookmarkRepository;
        this.threadRepository = threadRepository;
    }

    @Transactional
    public ThreadBookmarkStatusResponse watch(UUID userId, UUID threadId) {
        requireThread(threadId);
        if (!bookmarkRepository.existsByThreadIdAndUserId(threadId, userId)) {
            bookmarkRepository.save(ThreadBookmarkEntity.create(UUID.randomUUID(), threadId, userId, Instant.now()));
        }
        return new ThreadBookmarkStatusResponse(threadId, true);
    }

    @Transactional
    public ThreadBookmarkStatusResponse unwatch(UUID userId, UUID threadId) {
        requireThread(threadId);
        bookmarkRepository.deleteByThreadIdAndUserId(threadId, userId);
        return new ThreadBookmarkStatusResponse(threadId, false);
    }

    @Transactional(readOnly = true)
    public ThreadBookmarkStatusResponse status(UUID userId, UUID threadId) {
        requireThread(threadId);
        return new ThreadBookmarkStatusResponse(
                threadId,
                bookmarkRepository.existsByThreadIdAndUserId(threadId, userId));
    }

    @Transactional(readOnly = true)
    public ThreadPageResponse listWatched(UUID userId, int page, int size) {
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<ThreadEntity> result = bookmarkRepository.findWatchedThreads(userId, pageable);
        return new ThreadPageResponse(
                result.getContent().stream().map(ThreadMapper::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public Set<UUID> watchedAmong(UUID userId, Collection<UUID> threadIds) {
        if (threadIds == null || threadIds.isEmpty()) {
            return Set.of();
        }
        return new HashSet<>(bookmarkRepository.findWatchedThreadIds(userId, threadIds));
    }

    private void requireThread(UUID threadId) {
        threadRepository.findByIdAndDeletedAtIsNull(threadId)
                .orElseThrow(() -> new NotFoundException("THREAD_NOT_FOUND", "Thread not found"));
    }
}
