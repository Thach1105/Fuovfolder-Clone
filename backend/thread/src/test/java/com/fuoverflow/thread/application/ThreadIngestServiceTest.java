package com.fuoverflow.thread.application;

import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class ThreadIngestServiceTest {
    private final ThreadRepository repository = mock(ThreadRepository.class);
    private final ThreadIngestService service = new ThreadIngestService(repository);

    @Test
    void createsThreadWhenLocalIdUnknown() {
        UUID localId = UUID.randomUUID();
        when(repository.findById(localId)).thenReturn(Optional.empty());
        when(repository.save(any(ThreadEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ThreadEntity result = service.upsert(new ThreadIngestCommand(
                localId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Title", "title.1", "author", "https://x/threads/title.1/",
                Instant.now(), 3, 100L));

        assertThat(result.getId()).isEqualTo(localId);
        assertThat(result.getTitle()).isEqualTo("Title");
        assertThat(result.getReplyCount()).isEqualTo(3);
        assertThat(result.getViewCount()).isEqualTo(100L);
        assertThat(result.getImportedAuthorHandle()).isEqualTo("author");
        assertThat(result.getStatus()).isEqualTo("open");
    }

    @Test
    void updatesExistingThreadInPlace() {
        UUID localId = UUID.randomUUID();
        ThreadEntity existing = ThreadEntity.createImported(
                localId, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                "Old", "old.1", Instant.now(), Instant.now());
        when(repository.findById(localId)).thenReturn(Optional.of(existing));
        when(repository.save(any(ThreadEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        ThreadEntity result = service.upsert(new ThreadIngestCommand(
                localId, existing.getForumId(), existing.getCategoryId(), existing.getAuthorUserId(),
                "New title", "new.1", "author2", "https://x/threads/new.1/",
                Instant.now(), 5, 200L));

        assertThat(result.getId()).isEqualTo(localId);
        assertThat(result.getTitle()).isEqualTo("New title");
        assertThat(result.getSlug()).isEqualTo("new.1");
        assertThat(result.getReplyCount()).isEqualTo(5);
    }
}
