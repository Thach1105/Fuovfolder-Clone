package com.fuoverflow.thread.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.thread.api.dto.ThreadDetailResponse;
import com.fuoverflow.thread.api.dto.ThreadPageResponse;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ThreadQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final ThreadRepository threadRepository;

    public ThreadQueryService(ThreadRepository threadRepository) {
        this.threadRepository = threadRepository;
    }

    @Transactional(readOnly = true)
    public ThreadPageResponse browse(UUID forumId, UUID categoryId, int page, int size) {
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(
                safePage, safeSize,
                Sort.by(Sort.Order.desc("lastPostAt"), Sort.Order.desc("createdAt")));
        Page<ThreadEntity> result = threadRepository.browse(forumId, categoryId, pageable);
        return new ThreadPageResponse(
                result.getContent().stream().map(ThreadMapper::toSummary).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public ThreadDetailResponse getDetail(UUID threadId) {
        ThreadEntity thread = threadRepository.findByIdAndDeletedAtIsNull(threadId)
                .orElseThrow(() -> new NotFoundException("THREAD_NOT_FOUND", "Thread not found"));
        return ThreadMapper.toDetail(thread);
    }
}
