package com.fuoverflow.post.application;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.forum.PostChangeNotifier;
import com.fuoverflow.common.forum.ThreadLookup;
import com.fuoverflow.common.notification.NotificationPublisher;
import com.fuoverflow.post.api.dto.UpdatePostRequest;
import com.fuoverflow.post.persistence.PostEntity;
import com.fuoverflow.post.persistence.PostRepository;
import com.fuoverflow.thread.persistence.ThreadEntity;
import com.fuoverflow.thread.persistence.ThreadRepository;
import com.fuoverflow.user.application.PermissionResolverService;
import com.fuoverflow.user.application.UserLookupService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PostWriteServiceTest {
    @Mock
    private PostRepository postRepository;
    @Mock
    private ThreadRepository threadRepository;
    @Mock
    private ThreadLookup threadLookup;
    @Mock
    private UserLookupService userLookupService;
    @Mock
    private PermissionResolverService permissionResolver;
    @Mock
    private NotificationPublisher notificationPublisher;
    @Mock
    private PostChangeNotifier postChangeNotifier;

    @InjectMocks
    private PostWriteService postWriteService;

    @Test
    void updateAllowsAuthor() {
        UUID threadId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        PostEntity post = PostEntity.createUserPost(
                postId, threadId, authorId, null, "author", "body", "<p>body</p>", "visible", Instant.now());
        when(postRepository.findByIdAndThreadIdAndDeletedAtIsNull(postId, threadId)).thenReturn(Optional.of(post));
        when(postRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        var response = postWriteService.update(threadId, postId, authorId, new UpdatePostRequest("updated"));

        assertEquals(2, response.editVersion());
        verify(postChangeNotifier).postUpdated(postId, threadId);
    }

    @Test
    void updateRejectsNonAuthorWithoutModeratePermission() {
        UUID threadId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        UUID otherUserId = UUID.randomUUID();
        PostEntity post = PostEntity.createUserPost(
                postId, threadId, authorId, null, "author", "body", "<p>body</p>", "visible", Instant.now());
        when(postRepository.findByIdAndThreadIdAndDeletedAtIsNull(postId, threadId)).thenReturn(Optional.of(post));
        when(permissionResolver.hasPermission(otherUserId, "forum.post:moderate")).thenReturn(false);

        assertThrows(ForbiddenException.class, () ->
                postWriteService.update(threadId, postId, otherUserId, new UpdatePostRequest("updated")));
    }

    @Test
    void deleteRecalculatesThreadStats() {
        UUID threadId = UUID.randomUUID();
        UUID postId = UUID.randomUUID();
        UUID authorId = UUID.randomUUID();
        PostEntity post = PostEntity.createUserPost(
                postId, threadId, authorId, null, "author", "body", "<p>body</p>", "visible", Instant.now());
        ThreadEntity thread = ThreadEntity.createUserThread(
                threadId,
                UUID.randomUUID(),
                UUID.randomUUID(),
                authorId,
                "title",
                "title",
                "discussion",
                "author",
                null,
                null,
                null,
                null,
                Instant.now());
        thread.setReplyCount(1);

        when(postRepository.findByIdAndThreadIdAndDeletedAtIsNull(postId, threadId)).thenReturn(Optional.of(post));
        when(threadRepository.findByIdAndDeletedAtIsNull(threadId)).thenReturn(Optional.of(thread));
        when(postRepository.countByThreadIdAndDeletedAtIsNullAndStatusIn(threadId, List.of("visible"))).thenReturn(1L);
        when(postRepository.findFirstByThreadIdAndDeletedAtIsNullAndStatusInOrderByCreatedAtDesc(
                eq(threadId), eq(List.of("visible")))).thenReturn(Optional.empty());
        when(postRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(threadRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        postWriteService.delete(threadId, postId, authorId);

        assertEquals(0, thread.getReplyCount());
        verify(postChangeNotifier).postDeleted(postId, threadId);
    }
}
