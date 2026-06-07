package com.fuoverflow.material.application;

import com.fuoverflow.material.persistence.UploadedFileEntity;
import com.fuoverflow.material.persistence.UploadedFileRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Service
public class OrphanUploadCleanupJob {
    private static final Logger log = LoggerFactory.getLogger(OrphanUploadCleanupJob.class);

    private final UploadedFileRepository uploadedFileRepository;
    private final UploadService uploadService;

    public OrphanUploadCleanupJob(UploadedFileRepository uploadedFileRepository, UploadService uploadService) {
        this.uploadedFileRepository = uploadedFileRepository;
        this.uploadService = uploadService;
    }

    @Scheduled(fixedDelayString = "${fuoverflow.upload.orphan-cleanup-interval-ms:3600000}")
    @Transactional
    public void cleanupOrphans() {
        Instant cutoff = Instant.now().minus(24, ChronoUnit.HOURS);
        List<UploadedFileEntity> orphans = uploadedFileRepository.findOrphanCandidates(cutoff);
        if (orphans.isEmpty()) {
            return;
        }
        for (UploadedFileEntity orphan : orphans) {
            uploadService.deleteStoredFile(orphan);
        }
        log.info("Deleted {} orphan uploaded files", orphans.size());
    }
}
