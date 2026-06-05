package com.fuoverflow.notification.domain;

import java.util.UUID;

public record ThreadReplyNotificationPayload(
        UUID threadId,
        String threadTitle,
        UUID postId,
        String authorHandle
) {
    public String title() {
        return "Trả lời mới trong chủ đề bạn theo dõi";
    }

    public String body() {
        return (authorHandle != null && !authorHandle.isBlank() ? authorHandle : "Thành viên")
                + " đã trả lời trong \"" + threadTitle + "\"";
    }
}
