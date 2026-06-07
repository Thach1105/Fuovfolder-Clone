package com.fuoverflow.common.forum;

import java.util.Optional;
import java.util.UUID;

public interface ForumContentAccessChecker {
    Optional<UUID> findThreadIdForPost(UUID postId);

    boolean canReadThread(UUID viewerUserId, UUID threadId);
}
