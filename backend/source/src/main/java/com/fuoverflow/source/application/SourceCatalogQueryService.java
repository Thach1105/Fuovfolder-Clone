package com.fuoverflow.source.application;

import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.source.api.dto.CatalogItemDetailResponse;
import com.fuoverflow.source.api.dto.CatalogItemResponse;
import com.fuoverflow.source.api.dto.CatalogPageResponse;
import com.fuoverflow.source.config.SourceProperties;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourceRelatedItemEntity;
import com.fuoverflow.source.persistence.SourceRelatedItemRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SourceCatalogQueryService {
    private static final int MAX_PAGE_SIZE = 60;
    private static final int RELATED_LIMIT = 6;

    private final SourceCatalogItemRepository catalogRepository;
    private final SourceRelatedItemRepository relatedRepository;
    private final SourceProperties properties;

    public SourceCatalogQueryService(
            SourceCatalogItemRepository catalogRepository,
            SourceRelatedItemRepository relatedRepository,
            SourceProperties properties) {
        this.catalogRepository = catalogRepository;
        this.relatedRepository = relatedRepository;
        this.properties = properties;
    }

    @Transactional(readOnly = true)
    public CatalogPageResponse browse(String q, Boolean featured, String sort, int page, int size) {
        int safeSize = size > 0 ? Math.min(size, MAX_PAGE_SIZE) : properties.defaultPageSizeOrDefault();
        int safePage = Math.max(page, 0);
        String query = q != null && !q.isBlank() ? q.trim() : null;
        boolean featuredOnly = Boolean.TRUE.equals(featured);
        Pageable pageable = PageRequest.of(safePage, safeSize, resolveSort(sort));
        Page<SourceCatalogItemEntity> result = catalogRepository.browseActive(query, featuredOnly, pageable);
        return new CatalogPageResponse(
                result.getContent().stream().map(SourceCatalogMapper::toCard).toList(),
                result.getNumber(),
                result.getSize(),
                result.getTotalElements(),
                result.getTotalPages());
    }

    @Transactional(readOnly = true)
    public List<CatalogItemResponse> featured(int limit) {
        int safeLimit = limit > 0 ? Math.min(limit, MAX_PAGE_SIZE) : 8;
        return catalogRepository.findFeatured(PageRequest.of(0, safeLimit)).stream()
                .map(SourceCatalogMapper::toCard)
                .toList();
    }

    @Transactional
    public CatalogItemDetailResponse getDetail(String idOrCode) {
        SourceCatalogItemEntity item = resolveActive(idOrCode);
        catalogRepository.incrementViewCount(item.getId());
        return SourceCatalogMapper.toDetail(item, loadRelated(item.getId()));
    }

    private List<CatalogItemResponse> loadRelated(UUID catalogItemId) {
        List<SourceRelatedItemEntity> links = relatedRepository.findByCatalogItemIdOrderBySortOrderAsc(catalogItemId);
        return links.stream()
                .map(SourceRelatedItemEntity::getRelatedCatalogItemId)
                .map(catalogRepository::findByIdAndDeletedAtIsNull)
                .filter(java.util.Optional::isPresent)
                .map(java.util.Optional::get)
                .filter(SourceCatalogItemEntity::isActive)
                .limit(RELATED_LIMIT)
                .map(SourceCatalogMapper::toCard)
                .toList();
    }

    private SourceCatalogItemEntity resolveActive(String idOrCode) {
        java.util.Optional<SourceCatalogItemEntity> byId = tryParseUuid(idOrCode)
                .flatMap(catalogRepository::findByIdAndDeletedAtIsNull);
        SourceCatalogItemEntity item = byId
                .or(() -> catalogRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(idOrCode.trim()))
                .filter(SourceCatalogItemEntity::isActive)
                .orElseThrow(() -> new NotFoundException("CATALOG_NOT_FOUND", "Source item not found"));
        return item;
    }

    private static java.util.Optional<UUID> tryParseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private static Sort resolveSort(String sort) {
        if (sort == null || sort.isBlank()) {
            return Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("title"));
        }
        return switch (sort.trim().toLowerCase()) {
            case "newest" -> Sort.by(Sort.Order.desc("createdAt"));
            case "price_asc" -> Sort.by(Sort.Order.asc("pricePoints"));
            case "price_desc" -> Sort.by(Sort.Order.desc("pricePoints"));
            case "popular" -> Sort.by(Sort.Order.desc("viewCount"));
            default -> Sort.by(Sort.Order.asc("sortOrder"), Sort.Order.asc("title"));
        };
    }
}
