package com.fuoverflow.broadcast.application;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fuoverflow.broadcast.api.dto.BroadcastConfigRequest;
import com.fuoverflow.broadcast.api.dto.BroadcastConfigResponse;
import com.fuoverflow.broadcast.persistence.BroadcastConfigEntity;
import com.fuoverflow.broadcast.persistence.BroadcastConfigRepository;
import com.fuoverflow.common.exception.NotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

@Service
public class BroadcastConfigService {

    private final BroadcastConfigRepository configRepo;
    private final ObjectMapper objectMapper;

    public BroadcastConfigService(BroadcastConfigRepository configRepo,
                                  ObjectMapper objectMapper) {
        this.configRepo = configRepo;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<BroadcastConfigResponse> listAll() {
        return configRepo.findAll().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public BroadcastConfigResponse getByEventType(String eventType) {
        return toResponse(findByEventType(eventType));
    }

    @Transactional
    public BroadcastConfigResponse upsert(String eventType, BroadcastConfigRequest request) {
        String configJson = toJson(request.config());
        BroadcastConfigEntity entity = configRepo.findByEventType(eventType)
                .map(existing -> {
                    existing.updateConfig(configJson, request.enabled());
                    return existing;
                })
                .orElseGet(() -> {
                    BroadcastConfigEntity created = BroadcastConfigEntity.create(eventType, configJson);
                    if (!request.enabled()) {
                        created.updateConfig(configJson, false);
                    }
                    return created;
                });
        return toResponse(configRepo.save(entity));
    }

    @Transactional
    public BroadcastConfigResponse toggle(String eventType) {
        BroadcastConfigEntity entity = findByEventType(eventType);
        entity.updateConfig(entity.getConfigJson(), !entity.isEnabled());
        return toResponse(configRepo.save(entity));
    }

    private BroadcastConfigEntity findByEventType(String eventType) {
        return configRepo.findByEventType(eventType)
                .orElseThrow(() -> new NotFoundException(
                        "BROADCAST_CONFIG_NOT_FOUND",
                        "Broadcast config not found: " + eventType));
    }

    private BroadcastConfigResponse toResponse(BroadcastConfigEntity entity) {
        Map<String, Object> configMap;
        try {
            configMap = objectMapper.readValue(entity.getConfigJson(), new TypeReference<>() {});
        } catch (Exception e) {
            configMap = Map.of();
        }
        return new BroadcastConfigResponse(
                entity.getId(),
                entity.getEventType(),
                configMap,
                entity.isEnabled(),
                entity.getCreatedAt(),
                entity.getUpdatedAt());
    }

    private String toJson(Map<String, Object> map) {
        try {
            return objectMapper.writeValueAsString(map);
        } catch (Exception e) {
            return "{}";
        }
    }
}
