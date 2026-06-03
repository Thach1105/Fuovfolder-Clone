package com.fuoverflow.forum.api;

import com.fuoverflow.forum.api.dto.CategoryResponse;
import com.fuoverflow.forum.api.dto.CreateCategoryRequest;
import com.fuoverflow.forum.api.dto.UpdateCategoryRequest;
import com.fuoverflow.forum.application.CategoryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST API for Category management.
 * Public endpoints: GET
 * Admin endpoints: POST, PATCH, DELETE
 */
@RestController
@RequestMapping("/api/v1")
public class CategoryController {
    private final CategoryService categoryService;

    public CategoryController(CategoryService categoryService) {
        this.categoryService = categoryService;
    }

    /**
     * Get category by slug.
     * Public endpoint.
     */
    @GetMapping("/categories/{slug}")
    public CategoryResponse getCategoryBySlug(@PathVariable String slug) {
        return categoryService.getCategory(slug);
    }

    /**
     * Create new category in a forum.
     * Admin only (TODO: add @PreAuthorize when Spring Security is configured).
     */
    @PostMapping("/forums/{forumId}/categories")
    @ResponseStatus(HttpStatus.CREATED)
    // @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse createCategory(@PathVariable UUID forumId, @Valid @RequestBody CreateCategoryRequest request) {
        return categoryService.createCategory(forumId, request);
    }

    /**
     * Update category.
     * Admin only (TODO: add @PreAuthorize when Spring Security is configured).
     */
    @PatchMapping("/categories/{id}")
    // @PreAuthorize("hasRole('ADMIN')")
    public CategoryResponse updateCategory(@PathVariable UUID id, @Valid @RequestBody UpdateCategoryRequest request) {
        return categoryService.updateCategory(id, request);
    }

    /**
     * Soft delete category.
     * Admin only (TODO: add @PreAuthorize when Spring Security is configured).
     */
    @DeleteMapping("/categories/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    // @PreAuthorize("hasRole('ADMIN')")
    public void deleteCategory(@PathVariable UUID id) {
        categoryService.deleteCategory(id);
    }
}
