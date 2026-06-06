package com.fuoverflow.common.forum;

import java.util.UUID;

public interface PostChangeNotifier {
    void postUpdated(UUID postId, UUID threadId);

    void postDeleted(UUID postId, UUID threadId);
}
