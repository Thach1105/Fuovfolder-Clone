package com.fuoverflow.coursera.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface CourseraRequestItemRepository extends JpaRepository<CourseraRequestItemEntity, UUID> {
    List<CourseraRequestItemEntity> findByRequestId(UUID requestId);

    List<CourseraRequestItemEntity> findByRequestIdIn(Collection<UUID> requestIds);
}
