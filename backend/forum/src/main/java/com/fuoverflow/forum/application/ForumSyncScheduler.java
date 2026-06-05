package com.fuoverflow.forum.application;

import com.fuoverflow.forum.config.ForumSyncProperties;
import com.fuoverflow.forum.domain.SyncMode;
import com.fuoverflow.forum.domain.SyncScope;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Triggers recurring incremental crawls when {@code fuoverflow.forum.sync.enabled=true}.
 * The bean is not created unless explicitly enabled, so the default startup stays inert.
 */
@Component
@ConditionalOnProperty(prefix = "fuoverflow.forum.sync", name = "enabled", havingValue = "true")
public class ForumSyncScheduler {
    private static final Logger log = LoggerFactory.getLogger(ForumSyncScheduler.class);

    private final ForumSyncService syncService;
    private final ForumSyncProperties properties;

    public ForumSyncScheduler(ForumSyncService syncService, ForumSyncProperties properties) {
        this.syncService = syncService;
        this.properties = properties;
    }

    @Scheduled(fixedDelayString = "${fuoverflow.forum.sync.interval-ms:3600000}",
            initialDelayString = "${fuoverflow.forum.sync.interval-ms:3600000}")
    public void runScheduledSync() {
        SyncMode mode = SyncMode.from(properties.mode());
        SyncScope scope = SyncScope.from(properties.scope() != null ? properties.scope() : "incremental");
        log.info("Starting scheduled forum sync (mode={}, scope={})", mode.wireValue(), scope.wireValue());
        syncService.run(mode, scope, null);
    }
}
