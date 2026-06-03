package com.fuoverflow.forum.application;

import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.forum.api.dto.CategoryResponse;
import com.fuoverflow.forum.api.dto.CreateForumRequest;
import com.fuoverflow.forum.api.dto.ForumResponse;
import com.fuoverflow.forum.api.dto.UpdateForumRequest;
import com.fuoverflow.forum.persistence.ForumEntity;
import com.fuoverflow.forum.persistence.ForumRepository;
import com.fuoverflow.forum.persistence.ForumVisibility;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.util.List;
import java.util.UUID;

@Service
public class ForumService {
    private final ForumRepository forumRepository;
    private final CategoryService categoryService;
    private final Clock clock;

    public ForumService(ForumRepository forumRepository, CategoryService categoryService, Clock clock) {
        this.forumRepository = forumRepository;
        this.categoryService = categoryService;
        this.clock = clock;
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "forum:tree", key = "'all'")
    public List<ForumResponse> getForumTree() {
        var forums = forumRepository.findAllActive();

        return forums.stream()
                .map(forum -> {
                    List<CategoryResponse> categories = categoryService.getCategoriesByForumId(forum.getId());
                    long threadCount = categories.stream().mapToLong(CategoryResponse::threadCount).sum();
                    return ForumResponse.fromEntity(forum, threadCount, categories);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(value = "forum:meta", key = "#slug")
    public ForumResponse getForumBySlug(String slug) {
        var forum = forumRepository.findBySlugAndNotDeleted(slug)
                .orElseThrow(() -> new NotFoundException("FORUM_NOT_FOUND", "Forum not found: " + slug));

        List<CategoryResponse> categories = categoryService.getCategoriesByForumId(forum.getId());
        long threadCount = categories.stream().mapToLong(CategoryResponse::threadCount).sum();
        return ForumResponse.fromEntity(forum, threadCount, categories);
    }

    @Transactional
    @CacheEvict(value = "forum:tree", allEntries = true)
    public ForumResponse createForum(CreateForumRequest request, UUID createdByUserId) {
        if (forumRepository.existsBySlugAndNotDeleted(request.slug())) {
            throw new ConflictException("FORUM_SLUG_CONFLICT", "Forum slug already exists");
        }

        ForumVisibility visibility = request.visibility() != null ?
                ForumVisibility.valueOf(request.visibility()) : ForumVisibility.PUBLIC;

        var forum = ForumEntity.create(
                UUID.randomUUID(),
                request.slug(),
                request.title(),
                request.description(),
                visibility,
                request.sortOrder(),
                createdByUserId,
                clock.instant()
        );

        forumRepository.save(forum);
        return ForumResponse.fromEntity(forum, 0, List.of());
    }

    @Transactional
    @CacheEvict(value = {"forum:tree", "forum:meta"}, allEntries = true)
    public ForumResponse updateForum(UUID id, UpdateForumRequest request) {
        var forum = forumRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FORUM_NOT_FOUND", "Forum not found"));

        if (forum.isDeleted()) {
            throw new NotFoundException("FORUM_NOT_FOUND", "Forum not found");
        }

        ForumVisibility visibility = request.visibility() != null ?
                ForumVisibility.valueOf(request.visibility()) : null;

        forum.update(request.title(), request.description(), visibility, request.sortOrder(), clock.instant());
        forumRepository.save(forum);

        List<CategoryResponse> categories = categoryService.getCategoriesByForumId(forum.getId());
        long threadCount = categories.stream().mapToLong(CategoryResponse::threadCount).sum();
        return ForumResponse.fromEntity(forum, threadCount, categories);
    }

    @Transactional
    @CacheEvict(value = {"forum:tree", "forum:meta"}, allEntries = true)
    public void deleteForum(UUID id) {
        var forum = forumRepository.findById(id)
                .orElseThrow(() -> new NotFoundException("FORUM_NOT_FOUND", "Forum not found"));

        if (!forum.isDeleted()) {
            forum.softDelete(clock.instant());
            forumRepository.save(forum);
        }
    }
}
