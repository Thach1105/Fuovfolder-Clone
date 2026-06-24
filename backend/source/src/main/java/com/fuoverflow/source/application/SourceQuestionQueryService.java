package com.fuoverflow.source.application;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.common.exception.NotFoundException;
import com.fuoverflow.source.api.dto.PublicQuestionResponse;
import com.fuoverflow.source.persistence.SourceCatalogItemEntity;
import com.fuoverflow.source.persistence.SourceCatalogItemRepository;
import com.fuoverflow.source.persistence.SourceQuestionOptionRepository;
import com.fuoverflow.source.persistence.SourceQuestionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

@Service
public class SourceQuestionQueryService {
    private final SourceQuestionRepository questionRepository;
    private final SourceQuestionOptionRepository optionRepository;
    private final SourceCatalogItemRepository catalogRepository;
    private final SourceAccessGuard accessGuard;
    private final SourceMediaUrlResolver urlResolver;
    private final ObjectMapper objectMapper;

    public SourceQuestionQueryService(
            SourceQuestionRepository questionRepository,
            SourceQuestionOptionRepository optionRepository,
            SourceCatalogItemRepository catalogRepository,
            SourceAccessGuard accessGuard,
            SourceMediaUrlResolver urlResolver,
            ObjectMapper objectMapper) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.catalogRepository = catalogRepository;
        this.accessGuard = accessGuard;
        this.urlResolver = urlResolver;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<PublicQuestionResponse> listForUser(String idOrCode, UUID userId) {
        SourceCatalogItemEntity item = resolveActive(idOrCode);
        accessGuard.requireActiveAccess(userId, item.getId());
        return questionRepository.findByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()).stream()
                .map(q -> {
                    List<String> questionImageUrls = deserializeImageUrls(q.getQuestionImageUrls());
                    return urlResolver.resolvePublic(SourceQuestionMapper.toPublic(
                            q,
                            questionImageUrls,
                            optionRepository.findByQuestionIdOrderBySortOrderAsc(q.getId())));
                })
                .toList();
    }

    private SourceCatalogItemEntity resolveActive(String idOrCode) {
        java.util.Optional<SourceCatalogItemEntity> byId = tryParseUuid(idOrCode)
                .flatMap(catalogRepository::findByIdAndDeletedAtIsNull);
        return byId
                .or(() -> catalogRepository.findByCodeIgnoreCaseAndDeletedAtIsNull(idOrCode.trim()))
                .filter(SourceCatalogItemEntity::isActive)
                .orElseThrow(() -> new NotFoundException("CATALOG_NOT_FOUND", "Source item not found"));
    }

    private static java.util.Optional<UUID> tryParseUuid(String value) {
        try {
            return java.util.Optional.of(UUID.fromString(value.trim()));
        } catch (IllegalArgumentException ex) {
            return java.util.Optional.empty();
        }
    }

    private List<String> deserializeImageUrls(String json) {
        if (json == null || json.isBlank() || "[]".equals(json)) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json,
                    objectMapper.getTypeFactory().constructCollectionType(List.class, String.class));
        } catch (JsonProcessingException e) {
            return List.of();
        }
    }
}
