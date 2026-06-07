package com.fuoverflow.award.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AwardDefinitionRepository extends JpaRepository<AwardDefinitionEntity, UUID> {
    List<AwardDefinitionEntity> findByDeletedAtIsNullOrderByNameAsc();

    Optional<AwardDefinitionEntity> findByIdAndDeletedAtIsNull(UUID id);
}
