package com.fuoverflow.coursera.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface CourseraRequestStatusEventRepository extends JpaRepository<CourseraRequestStatusEventEntity, UUID> {
}
