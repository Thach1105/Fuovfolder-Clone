package com.fuoverflow.thread.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.thread.api.dto.ThreadBookmarkStatusResponse;
import com.fuoverflow.thread.api.dto.ThreadPageResponse;
import com.fuoverflow.thread.application.ThreadBookmarkService;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1")
public class ThreadBookmarkController {
    private final ThreadBookmarkService bookmarkService;

    public ThreadBookmarkController(ThreadBookmarkService bookmarkService) {
        this.bookmarkService = bookmarkService;
    }

    @GetMapping("/users/me/thread-bookmarks")
    @RequirePermission("forum.thread.bookmark:read")
    public ApiResponse<ThreadPageResponse> myBookmarks(
            Authentication authentication,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(bookmarkService.listWatched(userId, page, size));
    }

    @GetMapping("/threads/{threadId}/bookmark")
    @RequirePermission("forum.thread.bookmark:read")
    public ApiResponse<ThreadBookmarkStatusResponse> status(
            Authentication authentication,
            @PathVariable UUID threadId) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(bookmarkService.status(userId, threadId));
    }

    @PostMapping("/threads/{threadId}/bookmark")
    @RequirePermission("forum.thread.bookmark:create")
    public ApiResponse<ThreadBookmarkStatusResponse> watch(
            Authentication authentication,
            @PathVariable UUID threadId) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(bookmarkService.watch(userId, threadId));
    }

    @DeleteMapping("/threads/{threadId}/bookmark")
    @RequirePermission("forum.thread.bookmark:delete")
    public ApiResponse<ThreadBookmarkStatusResponse> unwatch(
            Authentication authentication,
            @PathVariable UUID threadId) {
        UUID userId = UUID.fromString(authentication.getName());
        return ApiResponse.ok(bookmarkService.unwatch(userId, threadId));
    }
}
