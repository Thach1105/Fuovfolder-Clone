package com.fuoverflow.forum.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.forum.api.dto.CategoryResponse;
import com.fuoverflow.forum.api.dto.CategoryTreeNodeResponse;
import com.fuoverflow.forum.api.dto.ForumResponse;
import com.fuoverflow.forum.persistence.CategoryEntity;
import com.fuoverflow.forum.persistence.CategoryRepository;
import com.fuoverflow.forum.persistence.ForumEntity;
import com.fuoverflow.forum.persistence.ForumRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class ForumQueryService {
    private final ForumRepository forumRepository;
    private final CategoryRepository categoryRepository;
    private final CategoryStatsService categoryStatsService;

    public ForumQueryService(
            ForumRepository forumRepository,
            CategoryRepository categoryRepository,
            CategoryStatsService categoryStatsService) {
        this.forumRepository = forumRepository;
        this.categoryRepository = categoryRepository;
        this.categoryStatsService = categoryStatsService;
    }

    @Transactional(readOnly = true)
    public List<ForumResponse> listForums() {
        return forumRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(ForumQueryService::toForum)
                .toList();
    }

    @Transactional(readOnly = true)
    public ForumResponse getForum(String forumSlug) {
        return toForum(requireForum(forumSlug));
    }

    @Transactional(readOnly = true)
    public List<CategoryResponse> listCategories(String forumSlug) {
        ForumEntity forum = requireForum(forumSlug);
        return categoryRepository
                .findByForumIdAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(forum.getId()).stream()
                .map(ForumQueryService::toCategory)
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CategoryTreeNodeResponse> listCategoryTree(String forumSlug) {
        ForumEntity forum = requireForum(forumSlug);
        List<CategoryEntity> all = categoryRepository
                .findByForumIdAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(forum.getId());
        CategoryStatsService.StatsBundle stats = categoryStatsService.statsForForum(forum.getId());

        Map<UUID, List<CategoryEntity>> childrenByParent = new HashMap<>();
        List<CategoryEntity> roots = new ArrayList<>();
        for (CategoryEntity category : all) {
            if (category.getParentId() == null) {
                roots.add(category);
            } else {
                childrenByParent.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
                        .add(category);
            }
        }
        return roots.stream()
                .map(root -> toTreeNode(root, childrenByParent, stats))
                .toList();
    }

    @Transactional(readOnly = true)
    public CategoryResponse getCategory(String forumSlug, String categorySlug) {
        ForumEntity forum = requireForum(forumSlug);
        CategoryEntity category = categoryRepository
                .findByForumIdAndSlugAndDeletedAtIsNull(forum.getId(), categorySlug)
                .orElseThrow(() -> new NotFoundException("CATEGORY_NOT_FOUND", "Category not found"));
        return toCategory(category);
    }

    private CategoryTreeNodeResponse toTreeNode(
            CategoryEntity category,
            Map<UUID, List<CategoryEntity>> childrenByParent,
            CategoryStatsService.StatsBundle stats) {
        CategoryStatsService.CategoryStats nodeStats = stats.get(category.getId());
        List<CategoryTreeNodeResponse> children = childrenByParent
                .getOrDefault(category.getId(), List.of()).stream()
                .map(child -> toTreeNode(child, childrenByParent, stats))
                .toList();
        return new CategoryTreeNodeResponse(
                category.getId(),
                category.getForumId(),
                category.getParentId(),
                category.getSlug(),
                category.getTitle(),
                category.getDescription(),
                category.getVisibility(),
                category.getIconColor(),
                category.getSortOrder(),
                nodeStats.topicCount(),
                nodeStats.postCount(),
                nodeStats.latestActivity(),
                children);
    }

    private ForumEntity requireForum(String forumSlug) {
        return forumRepository.findBySlugAndDeletedAtIsNull(forumSlug)
                .orElseThrow(() -> new NotFoundException("FORUM_NOT_FOUND", "Forum not found"));
    }

    private static ForumResponse toForum(ForumEntity f) {
        return new ForumResponse(
                f.getId(), f.getSlug(), f.getTitle(), f.getDescription(), f.getVisibility(), f.getCreatedAt());
    }

    private static CategoryResponse toCategory(CategoryEntity c) {
        return new CategoryResponse(
                c.getId(),
                c.getForumId(),
                c.getParentId(),
                c.getSlug(),
                c.getTitle(),
                c.getDescription(),
                c.getVisibility(),
                c.getIconColor(),
                c.getSortOrder());
    }
}
