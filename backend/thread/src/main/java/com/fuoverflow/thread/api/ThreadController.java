package com.fuoverflow.thread.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.thread.api.dto.ThreadDetailResponse;
import com.fuoverflow.thread.api.dto.ThreadPageResponse;
import com.fuoverflow.thread.application.ThreadQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads")
public class ThreadController {
    private final ThreadQueryService threadQueryService;

    public ThreadController(ThreadQueryService threadQueryService) {
        this.threadQueryService = threadQueryService;
    }

    @GetMapping
    public ApiResponse<ThreadPageResponse> browse(
            @RequestParam(required = false) UUID forumId,
            @RequestParam(required = false) UUID categoryId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(threadQueryService.browse(forumId, categoryId, page, size));
    }

    @GetMapping("/{threadId}")
    public ApiResponse<ThreadDetailResponse> get(@PathVariable UUID threadId) {
        return ApiResponse.ok(threadQueryService.getDetail(threadId));
    }
}
