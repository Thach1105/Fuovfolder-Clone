package com.fuoverflow.forum.api;

import com.fuoverflow.forum.api.dto.CreateForumRequest;
import com.fuoverflow.forum.api.dto.ForumResponse;
import com.fuoverflow.forum.api.dto.UpdateForumRequest;
import com.fuoverflow.forum.application.ForumService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

/**
 * REST API for Forum management.
 * Public endpoints: GET (list, detail)
 * Admin endpoints: POST, PATCH, DELETE
 */
@RestController
@RequestMapping("/api/v1/forums")
public class ForumController {
    private final ForumService forumService;

    public ForumController(ForumService forumService) {
        this.forumService = forumService;
    }

    /**
     * Get forum tree (all forums with their categories).
     * Public endpoint.
     */
    @GetMapping
    public List<ForumResponse> getForumTree() {
        return forumService.getForumTree();
    }

    /**
     * Get forum by slug.
     * Public endpoint.
     */
    @GetMapping("/{slug}")
    public ForumResponse getForumBySlug(@PathVariable String slug) {
        return forumService.getForumBySlug(slug);
    }

    /**
     * Create new forum.
     * Admin only (TODO: add @PreAuthorize when Spring Security is configured).
     */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    // @PreAuthorize("hasRole('ADMIN')")
    public ForumResponse createForum(@Valid @RequestBody CreateForumRequest request) {
        // TODO: Get current user ID from SecurityContext
        UUID createdByUserId = null;
        return forumService.createForum(request, createdByUserId);
    }

    /**
     * Update forum.
     * Admin only (TODO: add @PreAuthorize when Spring Security is configured).
     */
    @PatchMapping("/{id}")
    // @PreAuthorize("hasRole('ADMIN')")
    public ForumResponse updateForum(@PathVariable UUID id, @Valid @RequestBody UpdateForumRequest request) {
        return forumService.updateForum(id, request);
    }

    /**
     * Soft delete forum.
     * Admin only (TODO: add @PreAuthorize when Spring Security is configured).
     */
    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // @PreAuthorize("hasRole('ADMIN')")
    public void deleteForum(@PathVariable UUID id) {
        forumService.deleteForum(id);
    }
}
