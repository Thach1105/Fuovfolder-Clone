package com.fuoverflow.thread.application;

import com.fuoverflow.common.forum.ThreadWatcherLookup;
import com.fuoverflow.thread.persistence.ThreadBookmarkRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class ThreadWatcherLookupService implements ThreadWatcherLookup {
    private final ThreadBookmarkRepository bookmarkRepository;

    public ThreadWatcherLookupService(ThreadBookmarkRepository bookmarkRepository) {
        this.bookmarkRepository = bookmarkRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public List<UUID> watcherUserIdsExcluding(UUID threadId, UUID excludeUserId) {
        return bookmarkRepository.findWatcherUserIdsExcluding(threadId, excludeUserId);
    }
}
