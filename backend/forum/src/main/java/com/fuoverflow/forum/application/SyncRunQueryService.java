package com.fuoverflow.forum.application;

import com.fuoverflow.forum.api.dto.SyncRunPageResponse;
import com.fuoverflow.forum.api.dto.SyncRunResponse;
import com.fuoverflow.forum.persistence.ForumSyncRunEntity;
import com.fuoverflow.forum.persistence.ForumSyncRunRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class SyncRunQueryService {
    private static final int MAX_PAGE_SIZE = 50;

    private final ForumSyncRunRepository runRepository;

    public SyncRunQueryService(ForumSyncRunRepository runRepository) {
        this.runRepository = runRepository;
    }

    @Transactional(readOnly = true)
    public SyncRunPageResponse list(int page, int size) {
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : 20;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<ForumSyncRunEntity> result = runRepository.findAllByOrderByStartedAtDesc(pageable);
        return new SyncRunPageResponse(
                result.getContent().stream().map(SyncRunQueryService::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    public static SyncRunResponse toResponse(ForumSyncRunEntity e) {
        return new SyncRunResponse(
                e.getId(), e.getMode(), e.getScope(), e.getStatus(),
                e.getForumsSynced(), e.getThreadsSynced(), e.getPostsSynced(),
                e.getErrorMessage(), e.getStartedAt(), e.getFinishedAt());
    }
}
