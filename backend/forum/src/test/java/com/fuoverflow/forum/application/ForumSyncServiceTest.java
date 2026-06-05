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
import com.fuoverflow.post.application.PostIngestService;
import com.fuoverflow.thread.application.ThreadIngestService;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ForumSyncServiceTest {
    private final ForumCrawlerProperties properties = new ForumCrawlerProperties(
            "https://fuoverflow.com", null, 0, null, 0, null,
            List.of("hoi-dap"), 1, 1, 30);
    private final FuOverflowForumClient client = mock(FuOverflowForumClient.class);
    private final XenForoForumParser parser = mock(XenForoForumParser.class);
    private final ForumStructureService structureService = mock(ForumStructureService.class);
    private final ExternalMappingService mappingService = mock(ExternalMappingService.class);
    private final ThreadIngestService threadIngestService = mock(ThreadIngestService.class);
    private final PostIngestService postIngestService = mock(PostIngestService.class);
    private final ForumSyncRunRepository runRepository = mock(ForumSyncRunRepository.class);
    private final OutboxEventRepository outboxRepository = mock(OutboxEventRepository.class);

    private final ForumSyncService service = new ForumSyncService(
            properties, client, parser, structureService, mappingService,
            threadIngestService, postIngestService, runRepository, outboxRepository);

    @Test
    void runsFullSyncAndSkipsUnchangedPosts() {
        UUID forumId = UUID.randomUUID();
        UUID categoryId = UUID.randomUUID();
        UUID threadLocalId = UUID.randomUUID();
        UUID post1LocalId = UUID.randomUUID();
        UUID post2LocalId = UUID.randomUUID();

        when(runRepository.save(any(ForumSyncRunEntity.class))).thenAnswer(inv -> inv.getArgument(0));
        when(runRepository.findById(any(UUID.class)))
                .thenAnswer(inv -> Optional.of(ForumSyncRunEntity.start(inv.getArgument(0), "public", "full", Instant.now())));

        when(structureService.ensureForumAndCategory(eq("hoi-dap"), nullable(String.class)))
                .thenReturn(new ForumStructureService.ForumIds(forumId, categoryId));

        CrawledThreadSummary summary = new CrawledThreadSummary(
                "6575", "Title", "title.6575", "/threads/title.6575/",
                "author", Instant.now(), Instant.now(), 1, 50L);
        when(parser.parseForumListing(anyString(), eq("hoi-dap"), anyString()))
                .thenReturn(new CrawledForumListing("hoi-dap", "Hỏi Đáp", List.of(summary), 1, 1));

        CrawledPost post1 = new CrawledPost("16144", "author", "1", "<p>b1</p>", "b1", Instant.now(), "https://x/post-16144");
        CrawledPost post2 = new CrawledPost("16145", "verilog", "2", "<p>b2</p>", "b2", Instant.now(), "https://x/post-16145");
        when(parser.parseThreadPage(anyString(), eq("6575"), anyString()))
                .thenReturn(new CrawledThreadPage("6575", "Title", List.of(post1, post2), 1, 1));

        when(client.fetchForumPage(eq("hoi-dap"), anyInt(), nullable(String.class))).thenReturn("<html/>");
        when(client.fetchThreadPage(anyString(), anyInt(), nullable(String.class))).thenReturn("<html/>");

        Instant now = Instant.now();
        when(mappingService.resolve(eq(ExternalMappingService.TYPE_THREAD), eq("6575")))
                .thenReturn(mapping(threadLocalId, null, now));
        // post1 has no stored checksum -> must be written
        when(mappingService.resolve(eq(ExternalMappingService.TYPE_POST), eq("16144")))
                .thenReturn(mapping(post1LocalId, null, now));
        // post2 already stored with matching checksum -> must be skipped
        when(mappingService.resolve(eq(ExternalMappingService.TYPE_POST), eq("16145")))
                .thenReturn(mapping(post2LocalId, ContentChecksum.sha256("<p>b2</p>"), now));

        ForumSyncRunEntity result = service.run(SyncMode.PUBLIC, SyncScope.FULL, null);

        assertThat(result).isNotNull();
        verify(threadIngestService).upsert(any());
        verify(postIngestService, times(1)).upsert(any());
        verify(postIngestService, never()).upsert(org.mockito.ArgumentMatchers.argThat(
                cmd -> cmd != null && post2LocalId.equals(cmd.localId())));
        verify(mappingService).markSynced(any(UUID.class), anyString());
        verify(threadIngestService).applyAggregates(eq(threadLocalId), eq(post2LocalId), any(), eq(1));
    }

    private static ExternalContentMappingEntity mapping(UUID localId, String checksum, Instant now) {
        ExternalContentMappingEntity m = ExternalContentMappingEntity.create(
                UUID.randomUUID(), ExternalMappingService.SOURCE, "x", "x", localId, now);
        m.setContentChecksum(checksum);
        return m;
    }
}
