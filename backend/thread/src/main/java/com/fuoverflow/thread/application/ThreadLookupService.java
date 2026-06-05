package com.fuoverflow.thread.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.forum.ThreadLookup;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ThreadLookupService implements ThreadLookup {
    private final ThreadRepository threadRepository;

    public ThreadLookupService(ThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public ThreadInfo requireOpen(UUID threadId) {
        ThreadEntity thread = threadRepository.findByIdAndDeletedAtIsNull(threadId)
                .orElseThrow(() -> new NotFoundException("THREAD_NOT_FOUND", "Thread not found"));
        if (thread.getLockedAt() != null || "locked".equals(thread.getStatus())) {
            throw new BadRequestException("THREAD_LOCKED", "Thread is locked");
        }
        return new ThreadInfo(thread.getId(), thread.getForumId(), thread.getCategoryId(), thread.getLockedAt() != null);
    }
}
