package com.fuoverflow.coursera.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.coursera.api.dto.CatalogItemResponse;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemEntity;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class CourseraCatalogQueryService {
    private final CourseraCatalogItemRepository repository;

    public CourseraCatalogQueryService(CourseraCatalogItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> listActive(String q, Boolean featured) {
        boolean featuredOnly = Boolean.TRUE.equals(featured);
        String query = q != null && !q.isBlank() ? q.trim() : null;
        var items = query == null
                ? repository.listActive(featuredOnly)
                : repository.searchActive(query, featuredOnly);
        return items.stream()
                .map(CourseraCatalogMapper::toPublic)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogItemResponse getActive(UUID id) {
        CourseraCatalogItemEntity item = repository.findByIdAndDeletedAtIsNull(id)
                .filter(CourseraCatalogItemEntity::isActive)
                .orElseThrow(() -> new NotFoundException("CATALOG_NOT_FOUND", "Catalog item not found"));
        return CourseraCatalogMapper.toPublic(item);
    }
}
