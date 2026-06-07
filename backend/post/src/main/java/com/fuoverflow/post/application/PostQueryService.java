package com.fuoverflow.post.application;

import com.fuoverflow.material.api.dto.AttachmentResponse;
import com.fuoverflow.material.application.PostAttachmentService;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.post.api.dto.PostPageResponse;
import com.fuoverflow.post.api.dto.PostResponse;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import com.fuoverflow.user.persistence.UserEntity;
import com.fuoverflow.user.persistence.UserRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class PostQueryService {
    private static final int MAX_PAGE_SIZE = 50;
    private static final int DEFAULT_PAGE_SIZE = 20;

    private final PostRepository postRepository;
    private final PostVisibilityService postVisibilityService;
    private final PostAttachmentService postAttachmentService;
    private final UserRepository userRepository;
    private final UploadService uploadService;

    public PostQueryService(
            PostRepository postRepository,
            PostVisibilityService postVisibilityService,
            PostAttachmentService postAttachmentService,
            UserRepository userRepository,
            UploadService uploadService) {
        this.postRepository = postRepository;
        this.postVisibilityService = postVisibilityService;
        this.postAttachmentService = postAttachmentService;
        this.userRepository = userRepository;
        this.uploadService = uploadService;
    }

    @Transactional(readOnly = true)
    public PostPageResponse listByThread(UUID threadId, int page, int size) {
        UUID viewerUserId = resolveViewerUserId();
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : DEFAULT_PAGE_SIZE;
        int safePage = Math.max(page, 0);
        Pageable pageable = PageRequest.of(safePage, safeSize, Sort.by(Sort.Order.asc("createdAt")));
        List<String> statuses = postVisibilityService.listStatusesForViewer(viewerUserId);
        Page<PostEntity> result = postRepository.findByThreadIdAndStatuses(threadId, statuses, pageable);
        List<PostEntity> visiblePosts = result.getContent().stream()
                .filter(post -> postVisibilityService.canViewPost(viewerUserId, post.getAuthorUserId(), post.getStatus()))
                .toList();
        Map<UUID, String> avatarUrls = loadAvatarUrls(visiblePosts);
        Map<UUID, List<AttachmentResponse>> attachments = postAttachmentService.listForPosts(
                visiblePosts.stream().map(PostEntity::getId).toList());
        List<PostResponse> items = visiblePosts.stream()
                .map(post -> PostMapper.toResponse(
                        post,
                        avatarUrls.get(post.getAuthorUserId()),
                        attachments.getOrDefault(post.getId(), List.of())))
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
        List<PostEntity> posts = result.getContent();
        Map<UUID, String> avatarUrls = loadAvatarUrls(posts);
        Map<UUID, List<AttachmentResponse>> attachments = postAttachmentService.listForPosts(
                posts.stream().map(PostEntity::getId).toList());
        return new PostPageResponse(
                posts.stream()
                        .map(post -> PostMapper.toResponse(
                                post,
                                avatarUrls.get(post.getAuthorUserId()),
                                attachments.getOrDefault(post.getId(), List.of())))
                        .toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    private Map<UUID, String> loadAvatarUrls(List<PostEntity> posts) {
        Set<UUID> authorIds = posts.stream()
                .map(PostEntity::getAuthorUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (authorIds.isEmpty()) {
            return Map.of();
        }
        Map<UUID, String> avatarUrls = new HashMap<>();
        for (UserEntity user : userRepository.findAllById(authorIds)) {
            if (user.getDeletedAt() != null) {
                continue;
            }
            avatarUrls.put(user.getId(), uploadService.resolvePublicUrl(user.getAvatarUrl()));
        }
        return avatarUrls;
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
