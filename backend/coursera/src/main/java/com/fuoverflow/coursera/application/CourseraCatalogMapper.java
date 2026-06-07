package com.fuoverflow.coursera.application;

import com.fuoverflow.coursera.api.dto.AdminCatalogItemResponse;
import com.fuoverflow.coursera.api.dto.CatalogItemResponse;
import com.fuoverflow.coursera.persistence.CourseraCatalogItemEntity;

public final class CourseraCatalogMapper {
    private CourseraCatalogMapper() {
    }

    public static CatalogItemResponse toPublic(CourseraCatalogItemEntity e) {
        return new CatalogItemResponse(
                e.getId(),
                e.getCode(),
                e.getTitle(),
                e.getDescription(),
                e.getPricePoints(),
                e.getCoverImageUrl(),
                e.isFeatured());
    }

    public static AdminCatalogItemResponse toAdmin(CourseraCatalogItemEntity e) {
        return new AdminCatalogItemResponse(
                e.getId(),
                e.getCode(),
                e.getTitle(),
                e.getDescription(),
                e.getPricePoints(),
                e.getCoverImageUrl(),
                e.isActive(),
                e.isFeatured(),
                e.getSortOrder(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
