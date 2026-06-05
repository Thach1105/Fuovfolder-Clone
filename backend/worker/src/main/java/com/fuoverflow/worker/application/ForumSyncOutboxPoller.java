package com.fuoverflow.worker.application;

import com.fuoverflow.forum.persistence.OutboxEventEntity;
import com.fuoverflow.forum.persistence.OutboxEventRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;

/**
 * Polls the transactional outbox and processes forum-sync events. For the MVP this
 * acknowledges events (the hook where Redis/search invalidation will plug in) and marks
 * them done so the queue does not grow unbounded.
 */
@Component
public class ForumSyncOutboxPoller {
    private static final Logger log = LoggerFactory.getLogger(ForumSyncOutboxPoller.class);
    private static final int BATCH_SIZE = 50;
    private static final Set<String> KNOWN_TYPES = Set.of("forum_sync", "thread", "post");

    private final OutboxEventRepository outboxRepository;

    public ForumSyncOutboxPoller(OutboxEventRepository outboxRepository) {
        this.outboxRepository = outboxRepository;
    }

    @Scheduled(fixedDelayString = "${fuoverflow.worker.outbox.poll-interval-ms:30000}")
    @Transactional
    public void poll() {
        List<OutboxEventEntity> batch = outboxRepository
                .findByStatusAndAvailableAtLessThanEqualOrderByAvailableAtAsc(
                        "pending", Instant.now(), PageRequest.of(0, BATCH_SIZE));
        if (batch.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        for (OutboxEventEntity event : batch) {
            handle(event);
            event.setAttempts(event.getAttempts() + 1);
            event.setStatus("done");
            event.setProcessedAt(now);
        }
        outboxRepository.saveAll(batch);
        log.debug("Processed {} outbox event(s)", batch.size());
    }

    private void handle(OutboxEventEntity event) {
        if (!KNOWN_TYPES.contains(event.getAggregateType())) {
            log.warn("Acknowledging unknown outbox event type {} ({})",
                    event.getAggregateType(), event.getEventType());
            return;
        }
        // Cache/search invalidation hook: invalidate thread/forum caches and enqueue
        // search reindex jobs for the affected aggregate id once those subsystems exist.
        log.info("Outbox {} -> {} for {} {}",
                event.getId(), event.getEventType(), event.getAggregateType(), event.getAggregateId());
    }
}
