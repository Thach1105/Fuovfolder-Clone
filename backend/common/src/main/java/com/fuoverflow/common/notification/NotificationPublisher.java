package com.fuoverflow.common.notification;

import java.util.UUID;

public interface NotificationPublisher {
    void publishThreadReply(
            UUID threadId,
            String threadTitle,
            UUID postId,
            UUID authorUserId,
            String authorHandle);
}
