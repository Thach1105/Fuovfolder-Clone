package com.fuoverflow.coursera.application;

import com.fuoverflow.common.exception.ConflictException;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.coursera.api.dto.AdminCatalogItemResponse;
import com.fuoverflow.coursera.api.dto.CreateCatalogItemRequest;
import com.fuoverflow.coursera.api.dto.UpdateCatalogItemRequest;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemEntity;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class CourseraCatalogAdminService {
    private final CourseraCatalogItemRepository repository;

    public CourseraCatalogAdminService(CourseraCatalogItemRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AdminCatalogItemResponse> listAll() {
        return repository.findByDeletedAtIsNullOrderBySortOrderAscTitleAsc().stream()
                .map(CourseraCatalogMapper::toAdmin)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminCatalogItemResponse get(UUID id) {
        return CourseraCatalogMapper.toAdmin(requireItem(id));
    }

    @Transactional
    public AdminCatalogItemResponse create(CreateCatalogItemRequest request) {
        String code = normalizeCode(request.code());
        if (repository.existsByCodeIgnoreCaseAndDeletedAtIsNull(code)) {
            throw new ConflictException("CATALOG_CODE_EXISTS", "Catalog code already exists");
        }
        Instant now = Instant.now();
        CourseraCatalogItemEntity entity = CourseraCatalogItemEntity.create(
                UUID.randomUUID(),
                code,
                request.title().trim(),
                request.description(),
                request.pricePoints(),
                request.active() == null || request.active(),
                request.featured() != null && request.featured(),
                request.sortOrder() != null ? request.sortOrder() : 0,
                now);
        entity.setCoverImageUrl(request.coverImageUrl());
        return CourseraCatalogMapper.toAdmin(repository.save(entity));
    }

    @Transactional
    public AdminCatalogItemResponse update(UUID id, UpdateCatalogItemRequest request) {
        CourseraCatalogItemEntity entity = requireItem(id);
        Instant now = Instant.now();
        if (request.code() != null) {
            String code = normalizeCode(request.code());
            if (repository.existsByCodeIgnoreCaseAndDeletedAtIsNullAndIdNot(code, id)) {
                throw new ConflictException("CATALOG_CODE_EXISTS", "Catalog code already exists");
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
        if (request.coverImageUrl() != null) {
            entity.setCoverImageUrl(blankToNull(request.coverImageUrl()));
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
        entity.setUpdatedAt(now);
        return CourseraCatalogMapper.toAdmin(repository.save(entity));
    }

    @Transactional
    public void delete(UUID id) {
        CourseraCatalogItemEntity entity = requireItem(id);
        entity.setDeletedAt(Instant.now());
        entity.setActive(false);
        entity.setUpdatedAt(Instant.now());
        repository.save(entity);
    }

    @Transactional(readOnly = true)
    public long countActive() {
        return repository.listActive(false).size();
    }

    private CourseraCatalogItemEntity requireItem(UUID id) {
        return repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("CATALOG_NOT_FOUND", "Catalog item not found"));
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
