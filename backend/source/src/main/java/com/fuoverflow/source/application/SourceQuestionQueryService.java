package com.fuoverflow.source.application;

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

    public SourceQuestionQueryService(
            SourceQuestionRepository questionRepository,
            SourceQuestionOptionRepository optionRepository,
            SourceCatalogItemRepository catalogRepository,
            SourceAccessGuard accessGuard) {
        this.questionRepository = questionRepository;
        this.optionRepository = optionRepository;
        this.catalogRepository = catalogRepository;
        this.accessGuard = accessGuard;
    }

    @Transactional(readOnly = true)
    public List<PublicQuestionResponse> listForUser(String idOrCode, UUID userId) {
        SourceCatalogItemEntity item = resolveActive(idOrCode);
        accessGuard.requireActiveAccess(userId, item.getId());
        return questionRepository.findByCatalogItemIdAndDeletedAtIsNullOrderBySortOrderAsc(item.getId()).stream()
                .map(q -> SourceQuestionMapper.toPublic(
                        q,
                        optionRepository.findByQuestionIdOrderBySortOrderAsc(q.getId())))
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
}
