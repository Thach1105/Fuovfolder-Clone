package com.fuoverflow.forum.persistence;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {
    Optional<CategoryEntity> findByForumIdAndSlugAndDeletedAtIsNull(UUID forumId, String slug);

    List<CategoryEntity> findByForumIdAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(UUID forumId);

    List<CategoryEntity> findByForumIdAndParentIdIsNullAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(UUID forumId);

    List<CategoryEntity> findByForumIdAndParentIdAndDeletedAtIsNullOrderBySortOrderAscTitleAsc(
            UUID forumId, UUID parentId);
}
