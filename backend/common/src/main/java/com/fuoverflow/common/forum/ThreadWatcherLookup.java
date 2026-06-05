package com.fuoverflow.common.forum;

import java.util.List;
import java.util.UUID;

public interface ThreadWatcherLookup {
    List<UUID> watcherUserIdsExcluding(UUID threadId, UUID excludeUserId);
}
