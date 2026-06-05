package com.fuoverflow.thread.persistence;

import java.util.UUID;

public interface CategoryThreadStatsProjection {
    UUID getCategoryId();

    long getTopicCount();

    long getPostCount();
}
