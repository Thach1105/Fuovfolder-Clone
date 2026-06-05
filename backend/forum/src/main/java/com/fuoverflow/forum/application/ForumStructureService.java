package com.fuoverflow.forum.application;

import com.fuoverflow.forum.persistence.CategoryEntity;
import com.fuoverflow.forum.persistence.CategoryRepository;
import com.fuoverflow.forum.persistence.ForumEntity;
import com.fuoverflow.forum.persistence.ForumRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.UUID;

/**
 * Upserts the forum node and its default category for a crawled forum slug, returning
 * the local ids needed to attach threads. A single default category per forum keeps
 * the not-null {@code threads.category_id} satisfied without inventing structure the
 * source does not expose on listing pages.
 */
@Service
public class ForumStructureService {
    private final ForumRepository forumRepository;
    private final CategoryRepository categoryRepository;
    private final ExternalMappingService mappingService;

    public ForumStructureService(
            ForumRepository forumRepository,
            CategoryRepository categoryRepository,
            ExternalMappingService mappingService) {
        this.forumRepository = forumRepository;
        this.categoryRepository = categoryRepository;
        this.mappingService = mappingService;
    }

    public record ForumIds(UUID forumId, UUID categoryId) {
    }

    @Transactional
    public ForumIds ensureForumAndCategory(String forumSlug, String forumTitle) {
        String title = forumTitle != null && !forumTitle.isBlank() ? forumTitle : forumSlug;
        Instant now = Instant.now();

        UUID forumId = mappingService.resolve(ExternalMappingService.TYPE_FORUM, forumSlug).getLocalId();
        ForumEntity forum = forumRepository.findById(forumId).orElse(null);
        if (forum == null) {
            forum = ForumEntity.createImported(forumId, forumSlug, title, now);
        } else {
            forum.setSlug(forumSlug);
            forum.setTitle(title);
            forum.setDeletedAt(null);
        }
        forumRepository.save(forum);

        String categoryExternalId = forumSlug + ":general";
        UUID categoryId = mappingService.resolve(ExternalMappingService.TYPE_CATEGORY, categoryExternalId).getLocalId();
        CategoryEntity category = categoryRepository.findById(categoryId).orElse(null);
        if (category == null) {
            category = CategoryEntity.createImported(categoryId, forumId, forumSlug, title, now);
        } else {
            category.setForumId(forumId);
            category.setSlug(forumSlug);
            category.setTitle(title);
            category.setDeletedAt(null);
        }
        categoryRepository.save(category);

        return new ForumIds(forumId, categoryId);
    }
}
