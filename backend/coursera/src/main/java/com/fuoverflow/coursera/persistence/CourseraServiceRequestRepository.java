package com.fuoverflow.coursera.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface CourseraServiceRequestRepository
        extends JpaRepository<CourseraServiceRequestEntity, UUID>, JpaSpecificationExecutor<CourseraServiceRequestEntity> {
    Optional<CourseraServiceRequestEntity> findByUserIdAndIdempotencyKey(UUID userId, String idempotencyKey);

    Optional<CourseraServiceRequestEntity> findByIdAndUserId(UUID id, UUID userId);

    Page<CourseraServiceRequestEntity> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("""
            select r.status, count(r) from CourseraServiceRequestEntity r
            group by r.status
            """)
    java.util.List<Object[]> countByStatusAll();

    @Query("""
            select r.status, count(r) from CourseraServiceRequestEntity r
            where r.userId = :userId
            group by r.status
            """)
    java.util.List<Object[]> countByStatusForUser(@Param("userId") UUID userId);

    long countByUserId(UUID userId);
}
