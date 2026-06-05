package com.fuoverflow.post.application;

import com.fuoverflow.post.api.dto.PostPageResponse;
import com.fuoverflow.post.api.dto.PostResponse;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class PostQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PostRepository postRepository;

    public PostQueryService(PostRepository postRepository) {
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public PostPageResponse listByThread(UUID threadId, int page, int size) {
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("createdAt")));
        Page<PostEntity> result = postRepository.findByThreadIdAndDeletedAtIsNull(threadId, pageable);
        return new PostPageResponse(
                result.getContent().stream().map(PostQueryService::toResponse).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private static PostResponse toResponse(PostEntity p) {
        return new PostResponse(
                p.getId(),
                p.getThreadId(),
                p.getImportedAuthorHandle(),
                p.getBodyHtml(),
                p.getStatus(),
                p.getEditVersion(),
                p.getReactionCount(),
                p.getSourceUrl(),
                p.getCreatedAt());
    }
}
