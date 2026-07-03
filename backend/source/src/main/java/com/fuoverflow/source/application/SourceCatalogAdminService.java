package com.fuoverflow.source.application;

import com.fuoverflow.common.exception.BadRequestException;
import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.source.api.dto.AdminCatalogItemResponse;
import com.fuoverflow.source.api.dto.CreateCatalogItemRequest;
import com.fuoverflow.source.api.dto.UpdateCatalogItemRequest;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourceRelatedItemEntity;
import com.fuoverflow.source.persistence.SourceRelatedItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class SourceCatalogAdminService {
    private static final int DEFAULT_ACCESS_DAYS = 60;

    private final SourceCatalogItemRepository catalogRepository;
    private final SourceRelatedItemRepository relatedRepository;
    private final SourceMediaService mediaService;

    public SourceCatalogAdminService(
            SourceCatalogItemRepository catalogRepository,
            SourceRelatedItemRepository relatedRepository,
            SourceMediaService mediaService) {
        this.catalogRepository = catalogRepository;
        this.relatedRepository = relatedRepository;
        this.mediaService = mediaService;
    }

    @Transactional(readOnly = true)
    public List<AdminCatalogItemResponse> listAll() {
        return catalogRepository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(SourceCatalogMapper::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCatalogItemResponse get(UUID id) {
        return SourceCatalogMapper.toAdmin(requireItem(id));
    }

    @Transactional
    public AdminCatalogItemResponse create(CreateCatalogItemRequest request) {
        String code = normalizeCode(request.code());
        if (catalogRepository.existsByCodeIgnoreCaseAndDeletedAtIsNull(code)) {
            throw new ConflictException("CATALOG_CODE_EXISTS", "Source code already exists");
        }
        Instant now = Instant.now();
        SourceCatalogItemEntity entity = SourceCatalogItemEntity.create(
                UUID.randomUUID(),
                code,
                request.title().trim(),
                request.description(),
                request.pricePoints(),
                request.accessDays() != null ? request.accessDays() : DEFAULT_ACCESS_DAYS,
                0,
                request.duplicationRateBp() != null ? request.duplicationRateBp() : 0,
                request.passRateBp() != null ? request.passRateBp() : 0,
                request.cardColor(),
                request.coverImageUrl(),
                request.categorySlug(),
                request.active() == null || request.active(),
                request.featured() != null && request.featured(),
                request.sortOrder() != null ? request.sortOrder() : 0,
                now);
        SourceCatalogItemEntity saved = catalogRepository.save(entity);
        mediaService.markLinked(saved.getCoverImageUrl());
        return SourceCatalogMapper.toAdmin(saved);
    }

    @Transactional
    public AdminCatalogItemResponse update(UUID id, UpdateCatalogItemRequest request) {
        SourceCatalogItemEntity entity = requireItem(id);
        if (request.code() != null) {
            String code = normalizeCode(request.code());
            if (catalogRepository.existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(code, id)) {
                throw new ConflictException("CATALOG_CODE_EXISTS", "Source code already exists");
            }
            entity.setCode(code);
        }
        if (request.title() != null) {
            entity.setTitle(request.title().trim());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.pricePoints() != null) {
            entity.setPricePoints(request.pricePoints());
        }
        if (request.accessDays() != null) {
            entity.setAccessDays(request.accessDays());
        }
        // question_count is synced automatically from the question bank.
        if (request.duplicationRateBp() != null) {
            entity.setDuplicationRateBp(request.duplicationRateBp());
        }
        if (request.passRateBp() != null) {
            entity.setPassRateBp(request.passRateBp());
        }
        if (request.cardColor() != null) {
            entity.setCardColor(request.cardColor());
        }
        if (request.coverImageUrl() != null) {
            String oldCover = entity.getCoverImageUrl();
            String newCover = blankToNull(request.coverImageUrl());
            if (oldCover != null && !oldCover.equals(newCover)) {
                mediaService.unlinkStoredReference(oldCover);
            }
            entity.setCoverImageUrl(newCover);
        }
        if (request.categorySlug() != null) {
            entity.setCategorySlug(request.categorySlug());
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        if (request.featured() != null) {
            entity.setFeatured(request.featured());
        }
        if (request.sortOrder() != null) {
            entity.setSortOrder(request.sortOrder());
        }
        entity.setUpdatedAt(Instant.now());
        SourceCatalogItemEntity saved = catalogRepository.save(entity);
        mediaService.markLinked(saved.getCoverImageUrl());
        return SourceCatalogMapper.toAdmin(saved);
    }

    @Transactional
    public void delete(UUID id) {
        SourceCatalogItemEntity entity = requireItem(id);
        mediaService.deleteStoredReference(entity.getCoverImageUrl());
        Instant now = Instant.now();
        entity.setDeletedAt(now);
        entity.setActive(false);
        entity.setUpdatedAt(now);
        catalogRepository.save(entity);
    }

    @Transactional
    public void setRelated(UUID id, List<UUID> relatedIds) {
        requireItem(id);
        relatedRepository.deleteByCatalogItemId(id);
        if (relatedIds == null || relatedIds.isEmpty()) {
            return;
        }
        Instant now = Instant.now();
        int order = 0;
        for (UUID relatedId : relatedIds.stream().distinct().toList()) {
            if (relatedId.equals(id)) {
                throw new BadRequestException("INVALID_RELATED", "An item cannot be related to itself");
            }
            if (catalogRepository.findByIdAndDeletedAtIsNull(relatedId).isEmpty()) {
                throw new NotFoundException("RELATED_NOT_FOUND", "Related item not found: " + relatedId);
            }
            relatedRepository.save(SourceRelatedItemEntity.create(
                    UUID.randomUUID(), id, relatedId, order++, now));
        }
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return catalogRepository.countActive();
    }

    private SourceCatalogItemEntity requireItem(UUID id) {
        return catalogRepository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("CATALOG_NOT_FOUND", "Source item not found"));
    }

    private static String normalizeCode(String code) {
        return code.trim().toUpperCase();
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
