package com.fuoverflow.thread.persistence;

import java.time.Instant;
import java.util.UUID;

public interface CategoryLatestThreadProjection {
    UUID getCategoryId();

    UUID getThreadId();

    String getTitle();

    String getThreadType();

    String getAuthorHandle();

    Instant getLastPostAt();
}
