package com.fuoverflow.notification.application;

import com.fuoverflow.common.notification.NotificationPublisher;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class ForumNotificationPublisher implements NotificationPublisher {
    private final NotificationDispatchService dispatchService;

    public ForumNotificationPublisher(NotificationDispatchService dispatchService) {
        this.dispatchService = dispatchService;
    }

    @Override
    public void publishThreadReply(
            UUID threadId,
            String threadTitle,
            UUID postId,
            UUID authorUserId,
            String authorHandle) {
        dispatchService.dispatchThreadReply(threadId, threadTitle, postId, authorUserId, authorHandle);
    }
}
