package com.fuoverflow.forum.application;

import com.fuoverflow.forum.api.dto.CategoryLatestActivityResponse;
import com.fuoverflow.forum.persistence.CategoryEntity;
import com.fuoverflow.forum.persistence.CategoryRepository;
import com.fuoverflow.thread.persistence.CategoryLatestThreadProjection;
import com.fuoverflow.thread.persistence.CategoryThreadStatsProjection;
import com.fuoverflow.thread.persistence.ThreadRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@Service
public class CategoryStatsService {
    private final CategoryRepository categoryRepository;
    private final ThreadRepository threadRepository;

    public CategoryStatsService(CategoryRepository categoryRepository, ThreadRepository threadRepository) {
        this.categoryRepository = categoryRepository;
        this.threadRepository = threadRepository;
    }

    @Transactional(readOnly = true)
    public StatsBundle statsForForum(UUID forumId) {
        List<CategoryEntity> all = categoryRepository
                .findByForumIdAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(forumId);

        Map<UUID, CategoryStats> leafStats = loadLeafStats(forumId);

        Map<UUID, List<UUID>> childrenByParent = new HashMap<>();
        for (CategoryEntity category : all) {
            if (category.getParentId() != null) {
                childrenByParent.computeIfAbsent(category.getParentId(), ignored -> new ArrayList<>())
                        .add(category.getId());
            } else {
                childrenByParent.computeIfAbsent(category.getId(), ignored -> new ArrayList<>());
            }
        }

        Map<UUID, CategoryStats> aggregated = new HashMap<>();
        for (CategoryEntity category : all) {
            if (category.getParentId() == null) {
                aggregated.put(category.getId(), aggregateNode(category.getId(), childrenByParent, leafStats));
            } else {
                aggregated.put(category.getId(), leafStats.getOrDefault(category.getId(), CategoryStats.empty()));
            }
        }
        return new StatsBundle(aggregated);
    }

    private Map<UUID, CategoryStats> loadLeafStats(UUID forumId) {
        Map<UUID, CategoryStats> leafStats = new HashMap<>();

        for (CategoryThreadStatsProjection row : threadRepository.aggregateStatsByForum(forumId)) {
            leafStats.put(
                    row.getCategoryId(),
                    new CategoryStats((int) row.getTopicCount(), (int) row.getPostCount(), null));
        }

        for (CategoryLatestThreadProjection latest : threadRepository.latestThreadByCategoryForForum(forumId)) {
            CategoryLatestActivityResponse activity = new CategoryLatestActivityResponse(
                    latest.getThreadId(),
                    latest.getTitle(),
                    latest.getThreadType(),
                    latest.getAuthorHandle(),
                    latest.getLastPostAt());
            CategoryStats existing = leafStats.getOrDefault(latest.getCategoryId(), CategoryStats.empty());
            leafStats.put(
                    latest.getCategoryId(),
                    new CategoryStats(existing.topicCount(), existing.postCount(), activity));
        }
        return leafStats;
    }

    private CategoryStats aggregateNode(
            UUID parentId,
            Map<UUID, List<UUID>> childrenByParent,
            Map<UUID, CategoryStats> leafStats) {
        List<UUID> children = childrenByParent.getOrDefault(parentId, List.of());
        if (children.isEmpty()) {
            return leafStats.getOrDefault(parentId, CategoryStats.empty());
        }
        int topics = 0;
        int posts = 0;
        CategoryLatestActivityResponse latest = null;
        for (UUID childId : children) {
            CategoryStats child = aggregateNode(childId, childrenByParent, leafStats);
            if (child.latestActivity() != null
                    && (latest == null || child.latestActivity().postedAt().isAfter(latest.postedAt()))) {
                latest = child.latestActivity();
            }
            topics += child.topicCount();
            posts += child.postCount();
        }
        return new CategoryStats(topics, posts, latest);
    }

    public record CategoryStats(int topicCount, int postCount, CategoryLatestActivityResponse latestActivity) {
        static CategoryStats empty() {
            return new CategoryStats(0, 0, null);
        }
    }

    public record StatsBundle(Map<UUID, CategoryStats> byCategoryId) {
        public CategoryStats get(UUID categoryId) {
            return byCategoryId.getOrDefault(categoryId, CategoryStats.empty());
        }
    }
}
