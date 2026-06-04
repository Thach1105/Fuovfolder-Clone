package com.fuoverflow.coursera.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CourseraRequestCredentialRepository extends JpaRepository<CourseraRequestCredentialEntity, UUID> {
    Optional<CourseraRequestCredentialEntity> findByRequestId(UUID requestId);
}
