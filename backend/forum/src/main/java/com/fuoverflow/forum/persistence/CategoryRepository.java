package com.fuoverflow.forum.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CategoryRepository extends JpaRepository<CategoryEntity, UUID> {

    /**
     * Find a category by slug, excluding soft-deleted categories.
     */
    @Query("SELECT c FROM CategoryEntity c WHERE c.slug = :slug AND c.deletedAt IS NULL")
    Optional<CategoryEntity> findBySlugAndNotDeleted(@Param("slug") String slug);

    /**
     * Find all active categories belonging to a specific forum, ordered by sort_order.
     */
    @Query("SELECT c FROM CategoryEntity c WHERE c.forumId = :forumId AND c.deletedAt IS NULL ORDER BY c.sortOrder, c.title")
    List<CategoryEntity> findByForumIdAndNotDeleted(@Param("forumId") UUID forumId);

    /**
     * Find all active (not deleted) categories across all forums, ordered by forum and sort_order.
     */
    @Query("SELECT c FROM CategoryEntity c WHERE c.deletedAt IS NULL ORDER BY c.forumId, c.sortOrder, c.title")
    List<CategoryEntity> findAllActive();

    /**
     * Check if a category with the given slug exists within a forum (excluding deleted).
     */
    @Query("SELECT COUNT(c) > 0 FROM CategoryEntity c WHERE c.forumId = :forumId AND c.slug = :slug AND c.deletedAt IS NULL")
    boolean existsByForumIdAndSlugAndNotDeleted(@Param("forumId") UUID forumId, @Param("slug") String slug);

    /**
     * Check if a category exists by ID and is not deleted.
     */
    @Query("SELECT COUNT(c) > 0 FROM CategoryEntity c WHERE c.id = :id AND c.deletedAt IS NULL")
    boolean existsByIdAndNotDeleted(@Param("id") UUID id);

    /**
     * Count active categories in a forum.
     */
    @Query("SELECT COUNT(c) FROM CategoryEntity c WHERE c.forumId = :forumId AND c.deletedAt IS NULL")
    long countByForumIdAndNotDeleted(@Param("forumId") UUID forumId);
}
