package com.fuoverflow.award.application;

import com.fuoverflow.award.api.dto.AwardDefinitionResponse;
import com.fuoverflow.award.api.dto.UpdateAwardDefinitionRequest;
import com.fuoverflow.award.persistence.AwardDefinitionEntity;
import com.fuoverflow.award.persistence.AwardDefinitionRepository;
import com.fuoverflow.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class AwardDefinitionAdminService {
    private final AwardDefinitionRepository repository;

    public AwardDefinitionAdminService(AwardDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AwardDefinitionResponse> listAll() {
        return repository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public AwardDefinitionResponse update(UUID id, UpdateAwardDefinitionRequest request) {
        AwardDefinitionEntity entity = repository.findByIdAndDeletedAtIsNull(id)
                .orElseThrow(() -> new NotFoundException("AWARD_NOT_FOUND", "Award definition not found"));
        if (request.name() != null) {
            entity.setName(request.name().trim());
        }
        if (request.description() != null) {
            entity.setDescription(request.description());
        }
        if (request.iconUrl() != null) {
            entity.setIconUrl(blankToNull(request.iconUrl()));
        }
        if (request.active() != null) {
            entity.setActive(request.active());
        }
        entity.setUpdatedAt(Instant.now());
        return toResponse(repository.save(entity));
    }

    private AwardDefinitionResponse toResponse(AwardDefinitionEntity entity) {
        return new AwardDefinitionResponse(
                entity.getId(),
                entity.getSlug(),
                entity.getName(),
                entity.getDescription(),
                entity.getIconUrl(),
                entity.getAwardType(),
                entity.isActive());
    }

    private static String blankToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }
}
