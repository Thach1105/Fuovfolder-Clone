package com.fuoverflow.post.application;

import com.fuoverflow.post.api.dto.PostPageResponse;
import com.fuoverflow.post.api.dto.PostResponse;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.core.Authentication;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class PostQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PostRepository postRepository;
    private final PostVisibilityService postVisibilityService;

    public PostQueryService(PostRepository postRepository, PostVisibilityService postVisibilityService) {
        this.postRepository = postRepository;
        this.postVisibilityService = postVisibilityService;
    }

    @Transactional(readOnly = true)
    public PostPageResponse listByThread(UUID threadId, int page, int size) {
        UUID viewerUserId = resolveViewerUserId();
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("createdAt")));
        List<String> statuses = postVisibilityService.listStatusesForViewer(viewerUserId);
        Page<PostEntity> result = postRepository.findByThreadIdAndStatuses(threadId, statuses, pageable);
        List<PostResponse> items = result.getContent().stream()
                .filter(post -> postVisibilityService.canViewPost(viewerUserId, post.getAuthorUserId(), post.getStatus()))
                .map(PostMapper::toResponse)
                .toList();
        return new PostPageResponse(
                items,
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public PostPageResponse listPending(int page, int size) {
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize);
        Page<PostEntity> result = postRepository.findPendingPosts(pageable);
        return new PostPageResponse(
                result.getContent().stream().map(PostMapper::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private UUID resolveViewerUserId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || authentication instanceof AnonymousAuthenticationToken) {
            return null;
        }
        try {
            return UUID.fromString(authentication.getName());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }
}
