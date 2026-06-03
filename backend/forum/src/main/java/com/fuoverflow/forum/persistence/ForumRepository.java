package com.fuoverflow.forum.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface ForumRepository extends JpaRepository<ForumEntity, UUID> {

    /**
     * Find a forum by slug, excluding soft-deleted forums.
     */
    @Query("SELECT f FROM ForumEntity f WHERE f.slug = :slug AND f.deletedAt IS NULL")
    Optional<ForumEntity> findBySlugAndNotDeleted(@Param("slug") String slug);

    /**
     * Find all active (not deleted) forums, ordered by sort_order.
     */
    @Query("SELECT f FROM ForumEntity f WHERE f.deletedAt IS NULL ORDER BY f.sortOrder, f.title")
    List<ForumEntity> findAllActive();

    /**
     * Check if a forum with the given slug exists (excluding deleted).
     */
    @Query("SELECT COUNT(f) > 0 FROM ForumEntity f WHERE f.slug = :slug AND f.deletedAt IS NULL")
    boolean existsBySlugAndNotDeleted(@Param("slug") String slug);

    /**
     * Check if a forum exists by ID and is not deleted.
     */
    @Query("SELECT COUNT(f) > 0 FROM ForumEntity f WHERE f.id = :id AND f.deletedAt IS NULL")
    boolean existsByIdAndNotDeleted(@Param("id") UUID id);
}
