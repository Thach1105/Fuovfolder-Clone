package com.fuoverflow.forum.support;

/**
 * Raised when the crawler cannot retrieve a page after exhausting retries.
 */
public class ForumCrawlException extends RuntimeException {
    public ForumCrawlException(String message) {
        super(message);
    }

    public ForumCrawlException(String message, Throwable cause) {
        super(message, cause);
    }
}
