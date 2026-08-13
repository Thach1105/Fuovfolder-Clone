package com.fuoverflow.app.external.application;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuoverflow.app.external.api.dto.PublicApiPayloadResponse;
import com.fuoverflow.app.external.persistence.PublicApiPayloadEntity;
import com.fuoverflow.app.external.persistence.PublicApiPayloadRepository;
import com.fuoverflow.common.exception.BadRequestException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PublicApiPayloadService {

    private final PublicApiPayloadRepository payloadRepository;

    public PublicApiPayloadService(PublicApiPayloadRepository payloadRepository) {
        this.payloadRepository = payloadRepository;
    }

    @Transactional
    public PublicApiPayloadResponse save(JsonNode payload) {
        if (payload == null) {
            throw new BadRequestException("JSON_BODY_REQUIRED", "Request body must contain valid JSON");
        }

        PublicApiPayloadEntity entity = payloadRepository.save(
                PublicApiPayloadEntity.create(payload.toString()));
        return new PublicApiPayloadResponse(entity.getId(), entity.getCreatedAt());
    }
}
