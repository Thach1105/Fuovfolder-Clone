package com.fuoverflow.broadcast.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface AnnouncementRepository extends JpaRepository<AnnouncementEntity, UUID> {

    List<AnnouncementEntity> findByStatusOrderByPriorityDesc(String status);

    List<AnnouncementEntity> findByStatus(String status);

    List<AnnouncementEntity> findByStatusAndStartAtLessThanEqual(String status, Instant now);

    List<AnnouncementEntity> findByStatusAndEndAtLessThanEqual(String status, Instant now);
}
