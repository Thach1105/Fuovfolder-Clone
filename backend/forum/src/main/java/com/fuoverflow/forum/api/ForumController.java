package com.fuoverflow.forum.api;

import com.fuoverflow.common.security.RequirePermission;
import com.fuoverflow.common.web.ApiResponse;
import com.fuoverflow.forum.api.dto.CategoryResponse;
import com.fuoverflow.forum.api.dto.CategoryTreeNodeResponse;
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
    @RequirePermission(value = "forum:read", allowAnonymous = true)
    public ApiResponse<List<ForumResponse>> list() {
        return ApiResponse.ok(forumQueryService.listForums());
    }

    @GetMapping("/{forumSlug}")
    @RequirePermission(value = "forum:read", allowAnonymous = true)
    public ApiResponse<ForumResponse> get(@PathVariable String forumSlug) {
        return ApiResponse.ok(forumQueryService.getForum(forumSlug));
    }

    @GetMapping("/{forumSlug}/children")
    @RequirePermission(value = "forum:read", allowAnonymous = true)
    public ApiResponse<List<ForumResponse>> children(@PathVariable String forumSlug) {
        return ApiResponse.ok(forumQueryService.listChildForums(forumSlug));
    }

    @GetMapping("/{forumSlug}/categories")
    @RequirePermission(value = "forum.category:read", allowAnonymous = true)
    public ApiResponse<List<CategoryResponse>> categories(@PathVariable String forumSlug) {
        return ApiResponse.ok(forumQueryService.listCategories(forumSlug));
    }

    @GetMapping("/{forumSlug}/categories/tree")
    @RequirePermission(value = "forum.category:read", allowAnonymous = true)
    public ApiResponse<List<CategoryTreeNodeResponse>> categoryTree(@PathVariable String forumSlug) {
        return ApiResponse.ok(forumQueryService.listCategoryTree(forumSlug));
    }

    @GetMapping("/{forumSlug}/categories/{categorySlug}")
    @RequirePermission(value = "forum.category:read", allowAnonymous = true)
    public ApiResponse<CategoryResponse> category(
            @PathVariable String forumSlug,
            @PathVariable String categorySlug) {
        return ApiResponse.ok(forumQueryService.getCategory(forumSlug, categorySlug));
    }
}
