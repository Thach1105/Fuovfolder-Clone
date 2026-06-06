package com.fuoverflow.post.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.post.api.dto.CreatePostRequest;
import com.fuoverflow.post.api.dto.PostPageResponse;
import com.fuoverflow.post.api.dto.PostResponse;
import com.fuoverflow.post.api.dto.UpdatePostRequest;
import com.fuoverflow.post.application.PostQueryService;
import com.fuoverflow.post.application.PostWriteService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads/{threadId}/posts")
public class PostController {
    private final PostQueryService postQueryService;
    private final PostWriteService postWriteService;

    public PostController(PostQueryService postQueryService, PostWriteService postWriteService) {
        this.postQueryService = postQueryService;
        this.postWriteService = postWriteService;
    }

    @GetMapping
    @RequirePermission(value = "forum.post:read", allowAnonymous = true)
    public ApiResponse<PostPageResponse> listByThread(
            @PathVariable UUID threadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(postQueryService.listByThread(threadId, page, size));
    }

    @PostMapping
    @RequirePermission("forum.post:create")
    public ApiResponse<PostResponse> create(
            @PathVariable UUID threadId,
            Authentication authentication,
            @Valid @RequestBody CreatePostRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(postWriteService.reply(threadId, userId, request));
    }

    @PatchMapping("/{postId}")
    @RequirePermission("forum.post:update")
    public ApiResponse<PostResponse> update(
            @PathVariable UUID threadId,
            @PathVariable UUID postId,
            Authentication authentication,
            @Valid @RequestBody UpdatePostRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(postWriteService.update(threadId, postId, userId, request));
    }

    @DeleteMapping("/{postId}")
    @RequirePermission("forum.post:delete")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(
            @PathVariable UUID threadId,
            @PathVariable UUID postId,
            Authentication authentication) {
        UUID userId = UUID.fromString(authentication.getName());
        postWriteService.delete(threadId, postId, userId);
    }
}
