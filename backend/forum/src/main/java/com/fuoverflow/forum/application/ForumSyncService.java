package com.fuoverflow.forum.application;

import com.fuoverflow.forum.config.ForumCrawlerProperties;
import com.fuoverflow.forum.domain.CrawledForumListing;
import com.fuoverflow.forum.domain.CrawledPost;
import com.fuoverflow.forum.domain.CrawledThreadPage;
import com.fuoverflow.forum.domain.CrawledThreadSummary;
import com.fuoverflow.forum.domain.SyncMode;
import com.fuoverflow.forum.domain.SyncScope;
import com.fuoverflow.forum.persistence.ExternalContentMappingEntity;
import com.fuoverflow.forum.persistence.ForumSyncRunEntity;
import com.fuoverflow.forum.persistence.ForumSyncRunRepository;
import com.fuoverflow.forum.persistence.OutboxEventEntity;
import com.fuoverflow.forum.persistence.OutboxEventRepository;
import com.fuoverflow.forum.support.ContentChecksum;
import com.fuoverflow.forum.support.FuOverflowForumClient;
import com.fuoverflow.forum.support.XenForoForumParser;
import com.fuoverflow.post.application.PostIngestCommand;
import com.fuoverflow.post.application.PostIngestService;
import com.fuoverflow.thread.application.ThreadIngestCommand;
import com.fuoverflow.thread.application.ThreadIngestService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.UUID;

/**
 * Orchestrates a forum crawl run: fetch listing/thread pages, parse them, and upsert
 * forums/categories/threads/posts idempotently. HTTP work is deliberately kept outside
 * long DB transactions; each persistence step is its own short transaction and is safe
 * to re-run because local ids are stable per external source id.
 */
@Service
public class ForumSyncService {
    private static final Logger log = LoggerFactory.getLogger(ForumSyncService.class);

    /** Fixed system user seeded in V12 that owns all imported threads/posts. */
    private static final UUID IMPORT_USER_ID = UUID.fromString("00000000-0000-0000-0000-0000000000fe");

    private final ForumCrawlerProperties properties;
    private final FuOverflowForumClient client;
    private final XenForoForumParser parser;
    private final ForumStructureService structureService;
    private final ExternalMappingService mappingService;
    private final ThreadIngestService threadIngestService;
    private final PostIngestService postIngestService;
    private final ForumSyncRunRepository runRepository;
    private final OutboxEventRepository outboxRepository;

    public ForumSyncService(
            ForumCrawlerProperties properties,
            FuOverflowForumClient client,
            XenForoForumParser parser,
            ForumStructureService structureService,
            ExternalMappingService mappingService,
            ThreadIngestService threadIngestService,
            PostIngestService postIngestService,
            ForumSyncRunRepository runRepository,
            OutboxEventRepository outboxRepository) {
        this.properties = properties;
        this.client = client;
        this.parser = parser;
        this.structureService = structureService;
        this.mappingService = mappingService;
        this.threadIngestService = threadIngestService;
        this.postIngestService = postIngestService;
        this.runRepository = runRepository;
        this.outboxRepository = outboxRepository;
    }

    public ForumSyncRunEntity run(SyncMode mode, SyncScope scope, String cookieOverride) {
        Instant now = Instant.now();
        ForumSyncRunEntity runRecord = runRepository.save(
                ForumSyncRunEntity.start(UUID.randomUUID(), mode.wireValue(), scope.wireValue(), now));
        String cookie = mode == SyncMode.AUTHENTICATED
                ? (cookieOverride != null && !cookieOverride.isBlank() ? cookieOverride : properties.authCookie())
                : null;

        Counters counters = new Counters();
        try {
            for (String forumSlug : properties.forumSlugsOrDefault()) {
                syncForum(forumSlug, scope, cookie, counters);
            }
            finish(runRecord, "succeeded", counters, null);
            recordOutbox("forum_sync", runRecord.getId(), "FORUM_SYNC_COMPLETED",
                    "{\"runId\":\"" + runRecord.getId() + "\",\"threads\":" + counters.threads
                            + ",\"posts\":" + counters.posts + "}");
            log.info("Forum sync {} completed: {} forum(s), {} thread(s), {} post(s)",
                    runRecord.getId(), counters.forums, counters.threads, counters.posts);
        } catch (RuntimeException ex) {
            finish(runRecord, "failed", counters, truncate(ex.getMessage()));
            log.error("Forum sync {} failed: {}", runRecord.getId(), ex.getMessage(), ex);
        }
        return runRepository.findById(runRecord.getId()).orElse(runRecord);
    }

    private void syncForum(String forumSlug, SyncScope scope, String cookie, Counters counters) {
        int maxPages = scope == SyncScope.INCREMENTAL ? 1 : properties.maxThreadPagesPerForumOrDefault();
        int threadsRemaining = properties.maxThreadsPerForumOrDefault();
        ForumStructureService.ForumIds ids = null;
        int page = 1;
        int lastPage = 1;

        while (page <= maxPages && page <= lastPage && threadsRemaining > 0) {
            String html = client.fetchForumPage(forumSlug, page, cookie);
            CrawledForumListing listing = parser.parseForumListing(html, forumSlug, properties.baseUrlOrDefault());
            lastPage = Math.max(1, listing.lastPage());
            if (ids == null) {
                ids = structureService.ensureForumAndCategory(forumSlug, listing.forumTitle());
                counters.forums++;
            }
            for (CrawledThreadSummary summary : listing.threads()) {
                if (threadsRemaining <= 0) {
                    break;
                }
                if (summary.externalId() == null || summary.slug() == null) {
                    continue;
                }
                syncThread(ids, summary, cookie, counters);
                threadsRemaining--;
            }
            page++;
        }
    }

    private void syncThread(
            ForumStructureService.ForumIds ids, CrawledThreadSummary summary, String cookie, Counters counters) {
        UUID threadLocalId = mappingService.resolve(ExternalMappingService.TYPE_THREAD, summary.externalId()).getLocalId();
        String threadUrl = properties.baseUrlOrDefault() + (summary.path() != null ? summary.path() : "");

        threadIngestService.upsert(new ThreadIngestCommand(
                threadLocalId, ids.forumId(), ids.categoryId(), IMPORT_USER_ID,
                summary.title() != null ? summary.title() : "(untitled)",
                summary.slug(), summary.authorHandle(), threadUrl,
                summary.lastPostAt(), summary.replyCount(), summary.viewCount()));
        counters.threads++;

        int maxPostPages = properties.maxPostPagesPerThreadOrDefault();
        int page = 1;
        int lastPage = 1;
        int postCount = 0;
        UUID lastPostId = null;
        Instant lastPostAt = summary.lastPostAt();

        while (page <= maxPostPages && page <= lastPage) {
            String html = client.fetchThreadPage(summary.path(), page, cookie);
            CrawledThreadPage threadPage = parser.parseThreadPage(html, summary.externalId(), properties.baseUrlOrDefault());
            lastPage = Math.max(1, threadPage.lastPage());
            for (CrawledPost post : threadPage.posts()) {
                if (post.externalId() == null) {
                    continue;
                }
                UUID postLocalId = ingestPost(threadLocalId, post, counters);
                postCount++;
                lastPostId = postLocalId;
                if (post.postedAt() != null) {
                    lastPostAt = post.postedAt();
                }
            }
            page++;
        }

        int replyCount = Math.max(0, postCount - 1);
        threadIngestService.applyAggregates(threadLocalId, lastPostId, lastPostAt, replyCount);
        recordOutbox("thread", threadLocalId, "THREAD_SYNCED",
                "{\"threadId\":\"" + threadLocalId + "\"}");
    }

    private UUID ingestPost(UUID threadLocalId, CrawledPost post, Counters counters) {
        ExternalContentMappingEntity mapping =
                mappingService.resolve(ExternalMappingService.TYPE_POST, post.externalId());
        String checksum = ContentChecksum.sha256(post.bodyHtml());
        if (checksum.equals(mapping.getContentChecksum())) {
            return mapping.getLocalId();
        }
        postIngestService.upsert(new PostIngestCommand(
                mapping.getLocalId(), threadLocalId, IMPORT_USER_ID,
                post.bodyText() != null ? post.bodyText() : "",
                post.bodyHtml() != null ? post.bodyHtml() : "",
                post.authorHandle(), post.sourceUrl(), post.postedAt()));
        mappingService.markSynced(mapping.getId(), checksum);
        counters.posts++;
        return mapping.getLocalId();
    }

    private void finish(ForumSyncRunEntity runRecord, String status, Counters counters, String error) {
        runRecord.setStatus(status);
        runRecord.setForumsSynced(counters.forums);
        runRecord.setThreadsSynced(counters.threads);
        runRecord.setPostsSynced(counters.posts);
        runRecord.setErrorMessage(error);
        runRecord.setFinishedAt(Instant.now());
        runRepository.save(runRecord);
    }

    private void recordOutbox(String aggregateType, UUID aggregateId, String eventType, String payloadJson) {
        outboxRepository.save(OutboxEventEntity.create(
                UUID.randomUUID(), aggregateType, aggregateId, eventType, payloadJson, Instant.now()));
    }

    private static String truncate(String message) {
        if (message == null) {
            return "Unknown error";
        }
        return message.length() > 1000 ? message.substring(0, 1000) : message;
    }

    private static final class Counters {
        private int forums;
        private int threads;
        private int posts;
    }
}
