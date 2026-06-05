package com.fuoverflow.common.forum;

import java.util.UUID;

public interface ThreadLookup {
    ThreadInfo requireOpen(UUID threadId);

    record ThreadInfo(UUID id, UUID forumId, UUID categoryId, boolean locked) {
    }
}
