package com.fuoverflow.exam.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.exam.api.dto.CreateCommentRequest;
import com.fuoverflow.exam.api.dto.ExamCommentLikeResponse;
import com.fuoverflow.exam.api.dto.ExamCommentResponse;
import com.fuoverflow.exam.api.dto.UpdateCommentRequest;
import com.fuoverflow.exam.application.ExamCommentService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/exam/comments")
public class ExamCommentController {
    private final ExamCommentService commentService;

    public ExamCommentController(ExamCommentService commentService) {
        this.commentService = commentService;
    }

    @GetMapping("/{subjectType}/{subjectId}")
    @RequirePermission("exam.comment:read")
    public ApiResponse<List<ExamCommentResponse>> list(
            @PathVariable String subjectType,
            @PathVariable UUID subjectId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(commentService.list(subjectType, subjectId, userId));
    }

    @PostMapping("/{subjectType}/{subjectId}")
    @ResponseStatus(HttpStatus.CREATED)
    @RequirePermission("exam.comment:create")
    public ApiResponse<ExamCommentResponse> create(
            @PathVariable String subjectType,
            @PathVariable UUID subjectId,
            @Valid @RequestBody CreateCommentRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(commentService.create(subjectType, subjectId, userId, request));
    }

    @PutMapping("/{commentId}")
    @RequirePermission("exam.comment:update")
    public ApiResponse<ExamCommentResponse> update(
            @PathVariable UUID commentId,
            @Valid @RequestBody UpdateCommentRequest request,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(commentService.update(commentId, userId, request));
    }

    @PostMapping("/{commentId}/like")
    @RequirePermission("exam.comment:create")
    public ApiResponse<ExamCommentLikeResponse> toggleLike(
            @PathVariable UUID commentId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(commentService.toggleLike(commentId, userId));
    }

    @DeleteMapping("/{commentId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @RequirePermission("exam.comment:delete")
    public void delete(
            @PathVariable UUID commentId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        commentService.delete(commentId, userId);
    }
}
