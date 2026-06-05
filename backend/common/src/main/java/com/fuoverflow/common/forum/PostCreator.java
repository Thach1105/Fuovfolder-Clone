package com.fuoverflow.common.forum;

import java.util.UUID;

public interface PostCreator {
    UUID createInitialPost(UUID threadId, UUID authorUserId, String authorHandle, String body);
}
