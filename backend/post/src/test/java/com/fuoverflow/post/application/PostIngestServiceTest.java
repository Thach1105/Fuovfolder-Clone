package com.fuoverflow.post.application;

import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class PostIngestServiceTest {
    private final PostRepository repository = mock(PostRepository.class);
    private final PostIngestService service = new PostIngestService(repository);

    @Test
    void createsPostWhenLocalIdUnknown() {
        UUID localId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();
        when(repository.findById(localId)).thenReturn(Optional.empty());
        when(repository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostEntity result = service.upsert(new PostIngestCommand(
                localId, threadId, UUID.randomUUID(),
                "body text", "<p>body text</p>", "author", "https://x/post-1", Instant.now()));

        assertThat(result.getId()).isEqualTo(localId);
        assertThat(result.getThreadId()).isEqualTo(threadId);
        assertThat(result.getBodyHtml()).isEqualTo("<p>body text</p>");
        assertThat(result.getStatus()).isEqualTo("visible");
        assertThat(result.getEditVersion()).isEqualTo(1);
    }

    @Test
    void bumpsEditVersionWhenBodyChanges() {
        UUID localId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();
        PostEntity existing = PostEntity.createImported(
                localId, threadId, UUID.randomUUID(),
                "old", "<p>old</p>", Instant.now(), Instant.now());
        when(repository.findById(localId)).thenReturn(Optional.of(existing));
        when(repository.save(any(PostEntity.class))).thenAnswer(inv -> inv.getArgument(0));

        PostEntity result = service.upsert(new PostIngestCommand(
                localId, threadId, existing.getAuthorUserId(),
                "new", "<p>new</p>", "author", "https://x/post-1", existing.getCreatedAt()));

        assertThat(result.getBodyHtml()).isEqualTo("<p>new</p>");
        assertThat(result.getEditVersion()).isEqualTo(2);
    }
}
