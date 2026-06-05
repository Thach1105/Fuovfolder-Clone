package com.fuoverflow.forum.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface ExternalContentMappingRepository
        extends JpaRepository<ExternalContentMappingEntity, UUID> {
    Optional<ExternalContentMappingEntity> findBySourceAndExternalTypeAndExternalId(
            String source, String externalType, String externalId);
}
