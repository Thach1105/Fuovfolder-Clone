package com.fuoverflow.app.external.api;

import com.fasterxml.jackson.databind.JsonNode;
import com.fuoverflow.app.external.application.PublicApiPayloadService;
import com.fuoverflow.app.external.api.dto.PublicApiPayloadResponse;
import com.fuoverflow.common.web.ApiResponse;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/public")
public class PublicApiController {

    private final PublicApiPayloadService payloadService;

    public PublicApiController(PublicApiPayloadService payloadService) {
        this.payloadService = payloadService;
    }

    @PostMapping("/data")
    public ApiResponse<PublicApiPayloadResponse> receive(@RequestBody JsonNode payload) {
        return ApiResponse.ok(payloadService.save(payload));
    }
}
