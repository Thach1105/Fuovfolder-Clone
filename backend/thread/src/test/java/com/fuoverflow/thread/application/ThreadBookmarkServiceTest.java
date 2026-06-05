package com.fuoverflow.thread.application;

import com.fuoverflow.thread.persistence.ThreadBookmarkEntity;
import com.fuoverflow.thread.persistence.ThreadBookmarkRepository;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ThreadBookmarkServiceTest {
    @Mock
    ThreadBookmarkRepository bookmarkRepository;
    @Mock
    ThreadRepository threadRepository;
    @InjectMocks
    ThreadBookmarkService service;

    @Test
    void watchIsIdempotent() {
        UUID userId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();
        when(threadRepository.findByIdAndDeletedAtIsNull(threadId))
                .thenReturn(Optional.of(new ThreadEntity()));
        when(bookmarkRepository.existsByThreadIdAndUserId(threadId, userId)).thenReturn(true);

        var result = service.watch(userId, threadId);

        assertThat(result.watched()).isTrue();
        verify(bookmarkRepository, never()).save(any(ThreadBookmarkEntity.class));
    }

    @Test
    void unwatchRemovesBookmark() {
        UUID userId = UUID.randomUUID();
        UUID threadId = UUID.randomUUID();
        when(threadRepository.findByIdAndDeletedAtIsNull(threadId))
                .thenReturn(Optional.of(new ThreadEntity()));

        var result = service.unwatch(userId, threadId);

        assertThat(result.watched()).isFalse();
        verify(bookmarkRepository).deleteByThreadIdAndUserId(threadId, userId);
    }
}
