package com.fuoverflow.award.application;

import com.fuoverflow.award.api.dto.AwardDefinitionResponse;
import com.fuoverflow.award.persistence.AwardDefinitionEntity;
import com.fuoverflow.award.persistence.AwardDefinitionRepository;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class AwardDefinitionQueryService {
    private final AwardDefinitionRepository repository;

    public AwardDefinitionQueryService(AwardDefinitionRepository repository) {
        this.repository = repository;
    }

    @Transactional(readOnly = true)
    public List<AwardDefinitionResponse> listActive() {
        return repository.findByDeletedAtIsNullOrderByNameAsc().stream()
                .filter(AwardDefinitionEntity::isActive)
                .map(entity -> new AwardDefinitionResponse(
                        entity.getId(),
                        entity.getSlug(),
                        entity.getName(),
                        entity.getDescription(),
                        entity.getIconUrl(),
                        entity.getAwardType(),
                        entity.isActive()))
                .toList();
    }
}
