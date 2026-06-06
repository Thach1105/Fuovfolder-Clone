package com.fuoverflow.reaction.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import com.fuoverflow.reaction.api.dto.ReactionStatusResponse;
import com.fuoverflow.reaction.persistence.PostReactionEntity;
import com.fuoverflow.reaction.persistence.PostReactionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

@Service
public class ReactionService {
    private static final Set<String> ALLOWED_TYPES = Set.of("like");

    private final PostReactionRepository reactionRepository;
    private final PostRepository postRepository;

    public ReactionService(PostReactionRepository reactionRepository, PostRepository postRepository) {
        this.reactionRepository = reactionRepository;
        this.postRepository = postRepository;
    }

    @Transactional(readOnly = true)
    public ReactionStatusResponse status(UUID postId, UUID viewerUserId, String type) {
        String reactionType = normalizeType(type);
        requireVisiblePost(postId);
        long count = reactionRepository.countByPostIdAndReactionType(postId, reactionType);
        boolean reacted = viewerUserId != null
                && reactionRepository.findByPostIdAndUserIdAndReactionType(postId, viewerUserId, reactionType).isPresent();
        return new ReactionStatusResponse(reactionType, count, reacted);
    }

    @Transactional
    public ReactionStatusResponse add(UUID postId, UUID userId, String type) {
        String reactionType = normalizeType(type);
        PostEntity post = requireVisiblePost(postId);
        if (reactionRepository.findByPostIdAndUserIdAndReactionType(postId, userId, reactionType).isPresent()) {
            return status(postId, userId, reactionType);
        }
        reactionRepository.save(PostReactionEntity.create(postId, userId, reactionType, Instant.now()));
        post.setReactionCount((int) reactionRepository.countByPostIdAndReactionType(postId, reactionType));
        postRepository.save(post);
        return new ReactionStatusResponse(reactionType, post.getReactionCount(), true);
    }

    @Transactional
    public ReactionStatusResponse remove(UUID postId, UUID userId, String type) {
        String reactionType = normalizeType(type);
        PostEntity post = requireVisiblePost(postId);
        reactionRepository.deleteByPostIdAndUserIdAndReactionType(postId, userId, reactionType);
        post.setReactionCount((int) reactionRepository.countByPostIdAndReactionType(postId, reactionType));
        postRepository.save(post);
        return new ReactionStatusResponse(reactionType, post.getReactionCount(), false);
    }

    private PostEntity requireVisiblePost(UUID postId) {
        return postRepository.findById(postId)
                .filter(post -> post.getDeletedAt() == null && "visible".equals(post.getStatus()))
                .orElseThrow(() -> new NotFoundException("POST_NOT_FOUND", "Post not found"));
    }

    private String normalizeType(String type) {
        String normalized = type == null ? "like" : type.trim().toLowerCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(normalized)) {
            throw new BadRequestException("REACTION_TYPE_INVALID", "Reaction type is not supported");
        }
        return normalized;
    }
}
