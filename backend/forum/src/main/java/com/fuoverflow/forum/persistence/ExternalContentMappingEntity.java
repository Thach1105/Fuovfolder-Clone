package com.fuoverflow.forum.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;
import java.util.UUID;

/**
 * Stable mapping from an external source record (source + type + external id) to the
 * local row it produced. Enables idempotent re-crawling.
 */
@Entity
@Table(name = "external_content_mappings")
public class ExternalContentMappingEntity {
    @Id
    private UUID id;

    @Column(nullable = false, length = 64)
    private String source;

    @Column(name = "external_type", nullable = false, length = 32)
    private String externalType;

    @Column(name = "external_id", nullable = false, length = 190)
    private String externalId;

    @Column(name = "local_id", nullable = false)
    private UUID localId;

    @Column(name = "content_checksum", length = 64)
    private String contentChecksum;

    @Column(name = "last_synced_at", nullable = false)
    private Instant lastSyncedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    public UUID getId() { return id; }
    public String getSource() { return source; }
    public String getExternalType() { return externalType; }
    public String getExternalId() { return externalId; }
    public UUID getLocalId() { return localId; }
    public String getContentChecksum() { return contentChecksum; }
    public Instant getLastSyncedAt() { return lastSyncedAt; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }

    public void setContentChecksum(String contentChecksum) { this.contentChecksum = contentChecksum; }
    public void setLastSyncedAt(Instant lastSyncedAt) { this.lastSyncedAt = lastSyncedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public static ExternalContentMappingEntity create(
            UUID id, String source, String externalType, String externalId, UUID localId, Instant now) {
        ExternalContentMappingEntity e = new ExternalContentMappingEntity();
        e.id = id;
        e.source = source;
        e.externalType = externalType;
        e.externalId = externalId;
        e.localId = localId;
        e.lastSyncedAt = now;
        e.createdAt = now;
        e.updatedAt = now;
        return e;
    }
}
