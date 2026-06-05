package com.fuoverflow.notification.application;

import com.fuoverflow.common.forum.ThreadWatcherLookup;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationDispatchService {
    private final ThreadWatcherLookup threadWatcherLookup;
    private final NotificationService notificationService;

    public NotificationDispatchService(
            ThreadWatcherLookup threadWatcherLookup,
            NotificationService notificationService) {
        this.threadWatcherLookup = threadWatcherLookup;
        this.notificationService = notificationService;
    }

    @Async
    public void dispatchThreadReply(
            UUID threadId,
            String threadTitle,
            UUID postId,
            UUID authorUserId,
            String authorHandle) {
        List<UUID> recipients = threadWatcherLookup.watcherUserIdsExcluding(threadId, authorUserId);
        notificationService.createThreadReplyBatch(
                recipients,
                threadId,
                threadTitle,
                postId,
                authorHandle);
    }
}
