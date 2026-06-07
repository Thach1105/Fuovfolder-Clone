package com.fuoverflow.common.forum;

import java.util.List;
import java.util.UUID;

public interface PostCreator {
    UUID createInitialPost(
            UUID threadId,
            UUID authorUserId,
            String authorHandle,
            String body,
            List<UUID> attachmentFileIds);
}
