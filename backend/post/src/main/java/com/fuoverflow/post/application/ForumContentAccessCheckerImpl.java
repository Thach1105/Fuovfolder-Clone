package com.fuoverflow.post.application;

import com.fuoverflow.common.forum.ForumContentAccessChecker;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import com.fuoverflow.user.application.PermissionResolverService;
import org.springframework.stereotype.Service;

import java.util.Optional;
import java.util.UUID;

@Service
public class ForumContentAccessCheckerImpl implements ForumContentAccessChecker {
    private final PostRepository postRepository;
    private final PermissionResolverService permissionResolver;

    public ForumContentAccessCheckerImpl(
            PostRepository postRepository,
            PermissionResolverService permissionResolver) {
        this.postRepository = postRepository;
        this.permissionResolver = permissionResolver;
    }

    @Override
    public Optional<UUID> findThreadIdForPost(UUID postId) {
        return postRepository.findById(postId)
                .filter(post -> post.getDeletedAt() == null)
                .map(PostEntity::getThreadId);
    }

    @Override
    public boolean canReadThread(UUID viewerUserId, UUID threadId) {
        if (viewerUserId == null) {
            return true;
        }
        return permissionResolver.hasPermission(viewerUserId, "forum.post:read");
    }
}
