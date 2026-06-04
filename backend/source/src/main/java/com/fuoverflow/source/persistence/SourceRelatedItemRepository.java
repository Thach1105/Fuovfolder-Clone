package com.fuoverflow.source.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourceRelatedItemRepository extends JpaRepository<SourceRelatedItemEntity, UUID> {
    List<SourceRelatedItemEntity> findByCatalogItemIdOrderBySortOrderAsc(UUID catalogItemId);

    void deleteByCatalogItemId(UUID catalogItemId);
}
