package com.fuoverflow.source.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SourcePurchaseEventRepository extends JpaRepository<SourcePurchaseEventEntity, UUID> {
    List<SourcePurchaseEventEntity> findByPurchaseIdOrderByCreatedAtDesc(UUID purchaseId);
}
