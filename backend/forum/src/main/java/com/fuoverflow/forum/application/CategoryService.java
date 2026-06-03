package com.fuoverflow.forum.application;

import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.validation.ReferenceGuard;
import com.fuoverflow.forum.api.dto.CategoryResponse;
import com.fuoverflow.forum.api.dto.CreateCategoryRequest;
import com.fuoverflow.forum.api.dto.UpdateCategoryRequest;
import com.fuoverflow.forum.persistence.CategoryEntity;
import com.fuoverflow.forum.persistence.CategoryRepository;
import com.fuoverflow.forum.persistence.ForumVisibility;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class CategoryService {
    private final CategoryRepository categoryRepository;
    private final ReferenceGuard referenceGuard;
    private final Clock clock;

    public CategoryService(CategoryRepository categoryRepository, ReferenceGuard referenceGuard, Clock clock) {
        this.categoryRepository = categoryRepository;
        this.referenceGuard = referenceGuard;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "category:meta", key = "#slug")
    public CategoryResponse getCategory(String slug) {
        var category = categoryRepository.findBySlugAndNotDeleted(slug)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found: " + slug));

        // TODO: integrate with Redis counters for threadCount and postCount
        long threadCount = 0;
        long postCount = 0;

        return CategoryResponse.fromEntity(category, threadCount, postCount);
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> getCategoriesByForumId(UUID forumId) {
        var categories = categoryRepository.findByForumIdAndNotDeleted(forumId);

        return categories.stream()
                .map(cat -> {
                    // TODO: integrate with Redis counters
                    long threadCount = 0;
                    long postCount = 0;
                    return CategoryResponse.fromEntity(cat, threadCount, postCount);
                })
                .toList();
    }

    @Transactional
    @CacheEvict(value = "forum:tree", allEntries = true)
    public CategoryResponse createCategory(UUID forumId, CreateCategoryRequest request) {
        referenceGuard.requireForumExists(forumId);

        if (categoryRepository.existsByForumIdAndSlugAndNotDeleted(forumId, request.slug())) {
            throw new ConflictException("CATEGORY_SLUG_CONFLICT", "Category slug already exists in this forum");
        }

        ForumVisibility visibility = request.visibility() != null ?
                ForumVisibility.valueOf(request.visibility()) : ForumVisibility.PUBLIC;

        var category = CategoryEntity.create(
                UUID.randomUUID(),
                forumId,
                request.slug(),
                request.title(),
                request.description(),
                request.sortOrder(),
                visibility,
                clock.instant()
        );

        categoryRepository.save(category);
        return CategoryResponse.fromEntity(category, 0, 0);
    }

    @Transactional
    @CacheEvict(value = {"category:meta", "forum:tree"}, allEntries = true)
    public CategoryResponse updateCategory(UUID id, UpdateCategoryRequest request) {
        var category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found"));

        if (category.isDeleted()) {
            throw new NotFoundException("CATEGORY_NOT_FOUND", "Category not found");
        }

        ForumVisibility visibility = request.visibility() != null ?
                ForumVisibility.valueOf(request.visibility()) : null;

        category.update(
                request.title(),
                request.description(),
                request.sortOrder(),
                visibility,
                clock.instant()
        );

        categoryRepository.save(category);

        // TODO: fetch real counts
        return CategoryResponse.fromEntity(category, 0, 0);
    }

    @Transactional
    @CacheEvict(value = {"category:meta", "forum:tree"}, allEntries = true)
    public void deleteCategory(UUID id) {
        var category = categoryRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found"));

        if (!category.isDeleted()) {
            category.softDelete(clock.instant());
            categoryRepository.save(category);
        }
    }
}
