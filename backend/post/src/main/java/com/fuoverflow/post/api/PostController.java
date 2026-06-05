package com.fuoverflow.post.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.post.api.dto.PostPageResponse;
import com.fuoverflow.post.application.PostQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/threads/{threadId}/posts")
public class PostController {
    private final PostQueryService postQueryService;

    public PostController(PostQueryService postQueryService) {
        this.postQueryService = postQueryService;
    }

    @GetMapping
    public ApiResponse<PostPageResponse> listByThread(
            @PathVariable UUID threadId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return ApiResponse.ok(postQueryService.listByThread(threadId, page, size));
    }
}
