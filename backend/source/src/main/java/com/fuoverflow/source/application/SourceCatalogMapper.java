package com.fuoverflow.source.application;

import com.fuoverflow.source.api.dto.AdminCatalogItemResponse;
import com.fuoverflow.source.api.dto.CatalogItemDetailResponse;
import com.fuoverflow.source.api.dto.CatalogItemResponse;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;

import java.util.List;

public final class SourceCatalogMapper {
    private SourceCatalogMapper() {
    }

    public static CatalogItemResponse toCard(SourceCatalogItemEntity e) {
        return new CatalogItemResponse(
                e.getId(),
                e.getCode(),
                e.getTitle(),
                e.getPricePoints(),
                e.getAccessDays(),
                e.getQuestionCount(),
                e.getDuplicationRateBp() / 100.0,
                e.getPassRateBp() / 100.0,
                e.getViewCount(),
                e.getCardColor(),
                e.isFeatured());
    }

    public static CatalogItemDetailResponse toDetail(SourceCatalogItemEntity e, List<CatalogItemResponse> related) {
        return new CatalogItemDetailResponse(
                e.getId(),
                e.getCode(),
                e.getTitle(),
                e.getDescription(),
                e.getPricePoints(),
                e.getAccessDays(),
                e.getQuestionCount(),
                e.getDuplicationRateBp() / 100.0,
                e.getPassRateBp() / 100.0,
                e.getViewCount(),
                e.getCardColor(),
                e.getCategorySlug(),
                e.isFeatured(),
                related);
    }

    public static AdminCatalogItemResponse toAdmin(SourceCatalogItemEntity e) {
        return new AdminCatalogItemResponse(
                e.getId(),
                e.getCode(),
                e.getTitle(),
                e.getDescription(),
                e.getPricePoints(),
                e.getAccessDays(),
                e.getQuestionCount(),
                e.getDuplicationRateBp(),
                e.getPassRateBp(),
                e.getViewCount(),
                e.getCardColor(),
                e.getCategorySlug(),
                e.isActive(),
                e.isFeatured(),
                e.getSortOrder(),
                e.getCreatedAt(),
                e.getUpdatedAt());
    }
}
