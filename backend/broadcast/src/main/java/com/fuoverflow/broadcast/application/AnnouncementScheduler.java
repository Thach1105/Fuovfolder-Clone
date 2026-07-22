package com.fuoverflow.broadcast.application;

import com.fuoverflow.broadcast.persistence.AnnouncementEntity;
import com.fuoverflow.broadcast.persistence.AnnouncementRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class AnnouncementScheduler {

    private static final Logger log = LoggerFactory.getLogger(AnnouncementScheduler.class);

    private final AnnouncementRepository repo;
    private final AnnouncementService announcementService;

    public AnnouncementScheduler(AnnouncementRepository repo,
                                 AnnouncementService announcementService) {
        this.repo = repo;
        this.announcementService = announcementService;
    }

    @Scheduled(fixedDelayString = "${fuexam.announcement.scan-interval-ms:60000}")
    @Transactional
    public void processLifecycleTransitions() {
        Instant now = Instant.now();
        boolean changed = false;

        List<AnnouncementEntity> toActivate =
                repo.findByStatusAndStartAtLessThanEqual("SCHEDULED", now);
        for (AnnouncementEntity entity : toActivate) {
            entity.updateStatus("ACTIVE");
            repo.save(entity);
            log.info("Announcement activated: id={}, title={}", entity.getId(), entity.getTitle());
            changed = true;
        }

        List<AnnouncementEntity> toExpire =
                repo.findByStatusAndEndAtLessThanEqual("ACTIVE", now);
        for (AnnouncementEntity entity : toExpire) {
            entity.updateStatus("EXPIRED");
            repo.save(entity);
            log.info("Announcement expired: id={}, title={}", entity.getId(), entity.getTitle());
            changed = true;
        }

        if (changed) {
            announcementService.publishSync();
        }
    }
}
