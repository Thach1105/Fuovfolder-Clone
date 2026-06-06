package com.fuoverflow.common.forum;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Service
public class LoggingPostChangeNotifier implements PostChangeNotifier {
    private static final Logger log = LoggerFactory.getLogger(LoggingPostChangeNotifier.class);

    @Override
    public void postUpdated(UUID postId, UUID threadId) {
        log.debug("Post updated postId={} threadId={}", postId, threadId);
    }

    @Override
    public void postDeleted(UUID postId, UUID threadId) {
        log.debug("Post deleted postId={} threadId={}", postId, threadId);
    }
}
