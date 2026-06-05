package com.fuoverflow.forum.api;

import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.forum.api.dto.CategoryResponse;
import com.fuoverflow.forum.api.dto.ForumResponse;
import com.fuoverflow.forum.application.ForumQueryService;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/v1/forums")
public class ForumController {
    private final ForumQueryService forumQueryService;

    public ForumController(ForumQueryService forumQueryService) {
        this.forumQueryService = forumQueryService;
    }

    @GetMapping
    public ApiResponse<List<ForumResponse>> list() {
        return ApiResponse.ok(forumQueryService.listForums());
    }

    @GetMapping("/{forumSlug}/categories")
    public ApiResponse<List<CategoryResponse>> categories(@PathVariable String forumSlug) {
        return ApiResponse.ok(forumQueryService.listCategories(forumSlug));
    }
}
