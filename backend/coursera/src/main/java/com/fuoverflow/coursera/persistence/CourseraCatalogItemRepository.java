package com.fuoverflow.coursera.persistence;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CourseraCatalogItemRepository extends JpaRepository<CourseraCatalogItemEntity, UUID> {
    Optional<CourseraCatalogItemEntity> findByIdAndDeletedAtIsNull(UUID id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(String code, UUID id);

    boolean existsByCodeIgnoreCaseAndDeletedAtIsNull(String code);

    @Query("""
            select c from CourseraCatalogItemEntity c
            where c.deletedAt is null
              and c.active = true
              and (:featuredOnly = false or c.featured = true)
            order by c.sortOrder asc, c.title asc
            """)
    List<CourseraCatalogItemEntity> listActive(@Param("featuredOnly") boolean featuredOnly);

    @Query("""
            select c from CourseraCatalogItemEntity c
            where c.deletedAt is null
              and c.active = true
              and (lower(c.title) like lower(concat('%', :q, '%'))
                   or lower(c.code) like lower(concat('%', :q, '%')))
              and (:featuredOnly = false or c.featured = true)
            order by c.sortOrder asc, c.title asc
            """)
    List<CourseraCatalogItemEntity> searchActive(
            @Param("q") String q,
            @Param("featuredOnly") boolean featuredOnly);

    List<CourseraCatalogItemEntity> findByDeletedAtIsNullOrderBySortOrderAscTitleAsc();
}
