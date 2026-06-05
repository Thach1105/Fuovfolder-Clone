package com.fuoverflow.forum.api.dto;

/**
 * Admin request to trigger a crawl. {@code mode} is {@code public} or
 * {@code authenticated}; {@code scope} is {@code full} or {@code incremental}.
 * {@code cookie} optionally supplies a session cookie for authenticated mode at
 * runtime so credentials never need to be persisted.
 */
public record StartSyncRequest(
        String mode,
        String scope,
        String cookie
) {
}
