package com.fuoverflow.forum.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ForumRepository extends JpaRepository<ForumEntity, UUID> {
    Optional<ForumEntity> findBySlugAndDeletedAtIsNull(String slug);

    List<ForumEntity> findByDeletedAtIsNullOrderBySortOrderAscTitleAsc();
}
