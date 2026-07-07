package com.fuoverflow.source.application;

import com.fuoverflow.source.api.dto.CatalogItemDetailResponse;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class SourceCatalogMapperTest {

    @Test
    void toDetail_includesCoverImageUrl() {
        SourceCatalogItemEntity item = SourceCatalogItemEntity.create(
                UUID.randomUUID(),
                "MLN112",
                "Source title",
                "Description",
                150,
                30,
                12,
                4500,
                7800,
                "#112233",
                "catalog/cover-mln112.webp",
                "medicine",
                true,
                true,
                true,
                1,
                Instant.parse("2026-06-28T10:15:30Z"));

        CatalogItemDetailResponse detail = SourceCatalogMapper.toDetail(
                item,
                List.of(),
                Instant.parse("2026-07-28T10:15:30Z"));

        assertEquals("catalog/cover-mln112.webp", detail.coverImageUrl());
    }
}
