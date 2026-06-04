package com.fuoverflow.source.persistence;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface SourceCatalogItemRepository extends JpaRepository<SourceCatalogItemEntity, UUID> {
    Optional<SourceCatalogItemEntity> findByIdAndDeletedAtIsNull(UUID id);

    Optional<SourceCatalogItemEntity> findByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(String code, UUID id);

    @Query("""
            select c from SourceCatalogItemEntity c
            where c.deletedAt is null
              and c.active = true
              and (:featuredOnly = false or c.featured = true)
              and (:q is null
                   or lower(c.code) like lower(concat('%', :q, '%'))
                   or lower(c.title) like lower(concat('%', :q, '%')))
            """)
    Page<SourceCatalogItemEntity> browseActive(
            @Param("q") String q,
            @Param("featuredOnly") boolean featuredOnly,
            Pageable pageable);

    @Query("""
            select c from SourceCatalogItemEntity c
            where c.deletedAt is null and c.active = true and c.featured = true
            order by c.sortOrder asc, c.title asc
            """)
    List<SourceCatalogItemEntity> findFeatured(Pageable pageable);

    List<SourceCatalogItemEntity> findByDeletedAtIsNullOrderBySortOrderAscTitleAsc();

    @Query("""
            select count(c) from SourceCatalogItemEntity c
            where c.deletedAt is null and c.active = true
            """)
    long countActive();

    @Modifying
    @Query("update SourceCatalogItemEntity c set c.viewCount = c.viewCount + 1 where c.id = :id")
    void incrementViewCount(@Param("id") UUID id);
}
