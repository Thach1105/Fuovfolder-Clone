package com.fuoverflow.reaction.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface PostReactionRepository extends JpaRepository<PostReactionEntity, UUID> {
    Optional<PostReactionEntity> findByPostIdAndUserIdAndReactionType(UUID postId, UUID userId, String reactionType);

    long countByPostIdAndReactionType(UUID postId, String reactionType);

    void deleteByPostIdAndUserIdAndReactionType(UUID postId, UUID userId, String reactionType);
}
