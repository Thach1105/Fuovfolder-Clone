package com.fuoverflow.forum.application;

import com.fuoverflow.forum.persistence.ExternalContentMappingEntity;
import com.fuoverflow.forum.persistence.ExternalContentMappingRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Resolves stable local UUIDs for external source records and tracks per-record
 * content checksums so re-crawls can skip unchanged content.
 */
@Service
public class ExternalMappingService {
    public static final String SOURCE = "fuoverflow_forum";
    public static final String TYPE_FORUM = "forum";
    public static final String TYPE_CATEGORY = "category";
    public static final String TYPE_THREAD = "thread";
    public static final String TYPE_POST = "post";

    private final ExternalContentMappingRepository repository;

    public ExternalMappingService(ExternalContentMappingRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public ExternalContentMappingEntity resolve(String externalType, String externalId) {
        return repository.findBySourceAndExternalTypeAndExternalId(SOURCE, externalType, externalId)
                .orElseGet(() -> repository.save(ExternalContentMappingEntity.create(
                        UUID.randomUUID(), SOURCE, externalType, externalId, UUID.randomUUID(), Instant.now())));
    }

    @Transactional(readOnly = true)
    public boolean isUnchanged(String externalType, String externalId, String checksum) {
        if (checksum == null) {
            return false;
        }
        return repository.findBySourceAndExternalTypeAndExternalId(SOURCE, externalType, externalId)
                .map(m -> checksum.equals(m.getContentChecksum()))
                .orElse(false);
    }

    @Transactional
    public void markSynced(UUID mappingId, String checksum) {
        repository.findById(mappingId).ifPresent(mapping -> {
            mapping.setContentChecksum(checksum);
            mapping.setLastSyncedAt(Instant.now());
            repository.save(mapping);
        });
    }
}
