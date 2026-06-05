package com.fuoverflow.forum.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.forum.api.dto.CategoryResponse;
import com.fuoverflow.forum.api.dto.ForumResponse;
import com.fuoverflow.forum.persistence.CategoryEntity;
import com.fuoverflow.forum.persistence.CategoryRepository;
import com.fuoverflow.forum.persistence.ForumEntity;
import com.fuoverflow.forum.persistence.ForumRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class ForumQueryService {
    private final ForumRepository forumRepository;
    private final CategoryRepository categoryRepository;

    public ForumQueryService(ForumRepository forumRepository, CategoryRepository categoryRepository) {
        this.forumRepository = forumRepository;
        this.categoryRepository = categoryRepository;
    }

    @Transactional(readOnly = true)
    public List<ForumResponse> listForums() {
        return forumRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(ForumQueryService::toForum)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(String forumSlug) {
        ForumEntity forum = forumRepository.findBySlugAndDeletedAtIsNull(forumSlug)
                .orElseThrow(() -> new NotFoundException("FORUM_NOT_FOUND", "Forum not found"));
        return categoryRepository
                .findByForumIdAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(forum.getId()).stream()
                .map(ForumQueryService::toCategory)
                .toList();
    }

    private static ForumResponse toForum(ForumEntity f) {
        return new ForumResponse(
                f.getId(), f.getSlug(), f.getTitle(), f.getDescription(), f.getVisibility(), f.getCreatedAt());
    }

    private static CategoryResponse toCategory(CategoryEntity c) {
        return new CategoryResponse(
                c.getId(), c.getForumId(), c.getSlug(), c.getTitle(), c.getDescription(), c.getVisibility());
    }
}
