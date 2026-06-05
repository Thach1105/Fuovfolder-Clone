package com.fuoverflow.common.storage;

/**
 * Result of storing a binary object. {@code objectKey} is persisted in the database;
 * {@code publicUrl} is returned to clients for immediate preview.
 */
public record StoredObject(
        String objectKey,
        String publicUrl
) {
}
