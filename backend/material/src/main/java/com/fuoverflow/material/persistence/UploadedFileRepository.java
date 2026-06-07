package com.fuoverflow.material.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface UploadedFileRepository extends JpaRepository<UploadedFileEntity, UUID> {
    Optional<UploadedFileEntity> findByIdAndOwnerUserIdAndDeletedAtIsNull(UUID id, UUID ownerUserId);

    Optional<UploadedFileEntity> findByStoragePathAndOwnerUserIdAndDeletedAtIsNull(String storagePath, UUID ownerUserId);

    Optional<UploadedFileEntity> findByStoragePathAndDeletedAtIsNull(String storagePath);

    @Query("""
            select f from UploadedFileEntity f
            where f.linkedAt is null
              and f.deletedAt is null
              and f.status = 'active'
              and f.createdAt < :cutoff
            """)
    List<UploadedFileEntity> findOrphanCandidates(@Param("cutoff") Instant cutoff);

    List<UploadedFileEntity> findByIdInAndDeletedAtIsNull(Collection<UUID> ids);
}
