package com.fuoverflow.exam.application;

import com.fuoverflow.common.exception.ForbiddenException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.exam.api.dto.CreateCommentRequest;
import com.fuoverflow.exam.api.dto.ExamCommentLikeResponse;
import com.fuoverflow.exam.api.dto.ExamCommentResponse;
import com.fuoverflow.exam.api.dto.UpdateCommentRequest;
import com.fuoverflow.exam.persistence.ExamCommentEntity;
import com.fuoverflow.exam.persistence.ExamCommentLikeEntity;
import com.fuoverflow.exam.persistence.ExamCommentLikeRepository;
import com.fuoverflow.exam.persistence.ExamCommentRepository;
import com.fuoverflow.exam.persistence.ExamFeQuestionEntity;
import com.fuoverflow.exam.persistence.ExamFeQuestionRepository;
import com.fuoverflow.exam.persistence.ExamPeItemRepository;
import com.fuoverflow.exam.persistence.ExamSubjectRepository;
import com.fuoverflow.material.application.UploadService;
import com.fuoverflow.user.persistence.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ExamCommentServiceTest {
    @Mock private ExamCommentRepository commentRepository;
    @Mock private ExamFeQuestionRepository feQuestionRepository;
    @Mock private ExamPeItemRepository peItemRepository;
    @Mock private ExamSubjectRepository subjectRepository;
    @Mock private ExamAccessGuard accessGuard;
    @Mock private UserRepository userRepository;
    @Mock private UploadService uploadService;
    @Mock private ExamCommentLikeRepository likeRepository;

    private ExamCommentService service;
    private UUID userId;
    private UUID otherUserId;
    private UUID questionId;

    @BeforeEach
    void setUp() {
        service = new ExamCommentService(
                commentRepository, feQuestionRepository, peItemRepository,
                subjectRepository, accessGuard, userRepository, uploadService, likeRepository);
        userId = UUID.randomUUID();
        otherUserId = UUID.randomUUID();
        questionId = UUID.randomUUID();
        lenient().when(userRepository.findAllById(any())).thenReturn(List.of());
        lenient().when(likeRepository.findByCommentIdInAndUserId(any(), any())).thenReturn(List.of());
        lenient().when(likeRepository.existsByCommentIdAndUserId(any(), any())).thenReturn(false);
    }

    @Test
    void list_requiresMembership() {
        doThrow(new ForbiddenException("NO_ACTIVE_MEMBERSHIP", "no"))
                .when(accessGuard).requireActiveMembership(userId);
        assertThrows(ForbiddenException.class, () -> service.list("fe_question", questionId, userId));
    }

    @Test
    void create_persistsAndReturns() {
        when(feQuestionRepository.findByIdAndDeletedAtIsNull(questionId))
                .thenReturn(Optional.of(feQuestion()));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamCommentResponse res = service.create("fe_question", questionId, userId,
                new CreateCommentRequest("câu này đáp án A?", null));

        assertNotNull(res);
        assertEquals("fe_question", res.subjectType());
        verify(commentRepository).save(any());
    }

    @Test
    void create_unknownSubjectType_throws() {
        assertThrows(NotFoundException.class, () -> service.create("bogus", questionId, userId,
                new CreateCommentRequest("hi", null)));
    }

    @Test
    void update_rejectsNonAuthor() {
        ExamCommentEntity comment = comment(otherUserId);
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
        assertThrows(ForbiddenException.class, () -> service.update(comment.getId(), userId,
                new UpdateCommentRequest("edited")));
    }

    @Test
    void delete_rejectsNonAuthor() {
        ExamCommentEntity comment = comment(otherUserId);
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
        assertThrows(ForbiddenException.class, () -> service.delete(comment.getId(), userId));
    }

    @Test
    void delete_softDeletesForAuthor() {
        ExamCommentEntity comment = comment(userId);
        when(commentRepository.findByIdAndDeletedAtIsNull(comment.getId())).thenReturn(Optional.of(comment));
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.delete(comment.getId(), userId);

        assertNotNull(comment.getDeletedAt());
        verify(commentRepository).save(comment);
    }

    private ExamFeQuestionEntity feQuestion() {
        return ExamFeQuestionEntity.create(
                questionId, UUID.randomUUID(), "Q", "[]", "[]", 0, Instant.now(), UUID.randomUUID());
    }

    private ExamCommentEntity comment(UUID author) {
        return ExamCommentEntity.create(
                UUID.randomUUID(), "fe_question", questionId, UUID.randomUUID(),
                author, null, "body", "<p>body</p>", Instant.now());
    }

    @Test
    void toggleLike_likeThenUnlike() {
        UUID commentId = UUID.randomUUID();
        UUID userId = UUID.randomUUID();
        ExamCommentEntity comment = ExamCommentEntity.create(
                commentId, "fe_question", UUID.randomUUID(), UUID.randomUUID(),
                UUID.randomUUID(), null, "md", "html", Instant.now());
        when(commentRepository.findByIdAndDeletedAtIsNull(commentId)).thenReturn(Optional.of(comment));
        when(likeRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.empty());
        when(commentRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(likeRepository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        ExamCommentLikeResponse res = service.toggleLike(commentId, userId);
        assertTrue(res.liked());
        assertEquals(1, res.likeCount());

        // Second toggle — unlike
        ExamCommentLikeEntity likeEntity = ExamCommentLikeEntity.create(
                UUID.randomUUID(), commentId, userId, Instant.now());
        when(likeRepository.findByCommentIdAndUserId(commentId, userId)).thenReturn(Optional.of(likeEntity));

        ExamCommentLikeResponse res2 = service.toggleLike(commentId, userId);
        assertFalse(res2.liked());
        assertEquals(0, res2.likeCount());
    }
}
