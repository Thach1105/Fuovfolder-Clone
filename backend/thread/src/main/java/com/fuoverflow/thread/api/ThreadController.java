package com.fuoverflow.thread.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.thread.api.dto.CreateThreadRequest;
import com.fuoverflow.thread.api.dto.ThreadDetailResponse;
import com.fuoverflow.thread.api.dto.ThreadPageResponse;
import com.fuoverflow.thread.application.ThreadQueryService;
import com.fuoverflow.thread.application.ThreadWriteService;
import jakarta.validation.Valid;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads")
public class ThreadController {
    private final ThreadQueryService threadQueryService;
    private final ThreadWriteService threadWriteService;

    public ThreadController(ThreadQueryService threadQueryService, ThreadWriteService threadWriteService) {
        this.threadQueryService = threadQueryService;
        this.threadWriteService = threadWriteService;
    }

    @GetMapping
    @RequirePermission(value = "forum.thread:read", allowAnonymous = true)
    public ApiResponse<ThreadPageResponse> browse(
            @RequestParam(required = false) UUID forumId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(threadQueryService.browse(forumId, categoryId, page, size));
    }

    @GetMapping("/{threadId}")
    @RequirePermission(value = "forum.thread:read", allowAnonymous = true)
    public ApiResponse<ThreadDetailResponse> get(@PathVariable UUID threadId) {
        return ApiResponse.ok(threadQueryService.getDetail(threadId));
    }

    @PostMapping
    @RequirePermission("forum.thread:create")
    public ApiResponse<ThreadDetailResponse> create(
            Authentication authentication,
            @Valid @RequestBody CreateThreadRequest request) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(threadWriteService.create(userId, request));
    }
}
