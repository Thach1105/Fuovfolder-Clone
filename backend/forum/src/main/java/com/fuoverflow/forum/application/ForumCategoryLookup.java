package com.fuoverflow.forum.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.common.forum.CategoryLookup;
import com.fuoverflow.forum.persistence.CategoryEntity;
import com.fuoverflow.forum.persistence.CategoryRepository;
import com.fuoverflow.forum.persistence.ForumEntity;
import com.fuoverflow.forum.persistence.ForumRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
public class ForumCategoryLookup implements CategoryLookup {
    private final ForumRepository forumRepository;
    private final CategoryRepository categoryRepository;

    public ForumCategoryLookup(ForumRepository forumRepository, CategoryRepository categoryRepository) {
        this.forumRepository = forumRepository;
        this.categoryRepository = categoryRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryInfo requireActive(UUID categoryId) {
        CategoryEntity category = categoryRepository.findById(categoryId)
                .filter(c -> c.getDeletedAt() == null)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found"));
        return toInfo(category);
    }

    @Override
    @Transactional(readOnly = true)
    public CategoryInfo requireActive(String forumSlug, String categorySlug) {
        ForumEntity forum = forumRepository.findBySlugAndDeletedAtIsNull(forumSlug)
                .orElseThrow(() -> new NotFoundException("FORUM_NOT_FOUND", "Forum not found"));
        CategoryEntity category = categoryRepository.findByForumIdAndSlugAndDeletedAtIsNull(forum.getId(), categorySlug)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found"));
        return toInfo(category);
    }

    private static CategoryInfo toInfo(CategoryEntity category) {
        return new CategoryInfo(
                category.getId(),
                category.getForumId(),
                category.getParentId(),
                category.getSlug(),
                category.getTitle());
    }
}
